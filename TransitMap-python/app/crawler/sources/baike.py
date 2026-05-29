"""
数据源 — 百度百科

从百度百科解析城市地铁线路和站点数据。
目标页面格式: https://baike.baidu.com/item/{城市}地铁
"""

import re
import logging
from bs4 import BeautifulSoup
import httpx

from app.crawler.sources.base import BaseSource, CrawlResult, ScrapedLine, ScrapedStation

logger = logging.getLogger("tmap-python.crawler.baike")

BAIKE_SEARCH = "https://baike.baidu.com/search?word={query}"
BAIKE_PAGE = "https://baike.baidu.com/item/{title}"


class BaikeSource(BaseSource):
    """百度百科数据源"""

    @property
    def name(self) -> str:
        return "baike"

    async def crawl(self, city_name: str) -> CrawlResult:
        """从百度百科爬取城市地铁数据"""
        result = CrawlResult(source=self.name, city_name=city_name)

        try:
            # 尝试直接访问百科页面
            titles = self._generate_titles(city_name)
            html = None
            for title in titles:
                html = await self._fetch_page(title)
                if html:
                    break

            if not html:
                # 尝试搜索
                for title in titles:
                    html = await self._search_and_fetch(title)
                    if html:
                        break

            if not html:
                result.success = False
                result.error = f"未找到「{city_name}」的百度百科地铁页面"
                return result

            soup = BeautifulSoup(html, "lxml")

            # 解析内容
            lines, stations = self._parse_content(soup, city_name)
            result.lines = lines
            result.stations = stations

            logger.info(f"[baike] {city_name}: {len(lines)} 条线路, {len(stations)} 个站点")

        except Exception as e:
            result.success = False
            result.error = str(e)
            logger.error(f"[baike] {city_name} 爬取失败: {e}")

        return result

    def _generate_titles(self, city_name: str) -> list[str]:
        """生成可能的百科页面标题"""
        short = city_name.replace("市", "").replace("地区", "")
        return [
            f"{short}地铁",
            f"{short}轨道交通",
            f"{short}市地铁",
        ]

    async def _fetch_page(self, title: str) -> str | None:
        """获取百科页面"""
        url = BAIKE_PAGE.format(title=title)
        try:
            async with httpx.AsyncClient(timeout=15, follow_redirects=True) as client:
                resp = await client.get(url, headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Accept-Language": "zh-CN,zh;q=0.9",
                })
                if resp.status_code == 200 and len(resp.text) > 1000:
                    return resp.text
                return None
        except Exception as e:
            logger.debug(f"获取 {url} 失败: {e}")
            return None

    async def _search_and_fetch(self, query: str) -> str | None:
        """搜索百科并获取第一个结果页面"""
        url = BAIKE_SEARCH.format(query=query)
        try:
            async with httpx.AsyncClient(timeout=15, follow_redirects=True) as client:
                resp = await client.get(url, headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                })
                if resp.status_code != 200:
                    return None

                soup = BeautifulSoup(resp.text, "lxml")
                # 从搜索结果中找到第一个匹配的链接
                for link in soup.find_all("a"):
                    href = link.get("href", "")
                    text = link.get_text(strip=True)
                    if "/item/" in href and "地铁" in text:
                        # 获取完整 URL
                        if href.startswith("/item/"):
                            page_title = href.replace("/item/", "").split("?")[0]
                            return await self._fetch_page(page_title)
                return None
        except Exception:
            return None

    def _parse_content(self, soup: BeautifulSoup, city_name: str) -> tuple[list[ScrapedLine], list[ScrapedStation]]:
        """解析百科页面内容"""
        lines = []
        stations = []
        seen_lines = set()
        seen_stations = set()

        # 获取正文内容
        content = soup.find("div", class_="lemmaWgt-content-body") or \
                  soup.find("div", class_="content") or \
                  soup.find("div", id="content")

        if not content:
            content = soup

        # 从正文中提取线路信息
        # 百科通常在正文中提到"X号线"等信息
        text = content.get_text()

        # 提取线路名
        line_pattern = re.compile(r"([一-龥]+?\d+号线|[一-龥]*环线|[东西南北]延线)")
        for match in line_pattern.finditer(text):
            name = match.group(1).strip()
            if name not in seen_lines:
                seen_lines.add(name)
                lines.append(ScrapedLine(name=name))

        # 从表格中提取站点
        for table in content.find_all("table"):
            rows = table.find_all("tr")
            if len(rows) < 3:
                continue

            # 检查是否是站点表
            header_text = rows[0].get_text()
            if "站" not in header_text and "车站" not in header_text:
                continue

            # 尝试提取当前线路名（通常在表格上方或标题中）
            current_line = self._extract_line_from_context(table)

            for row in rows[1:]:
                cells = row.find_all(["td", "th"])
                if len(cells) < 1:
                    continue

                # 尝试从第一个单元格提取站名
                station_name = cells[0].get_text(strip=True)
                station_name = self._clean_station_name(station_name)

                if not station_name or station_name in seen_stations:
                    continue

                # 检查是否是有效站名
                if not re.search(r"站|换乘", station_name):
                    continue

                seen_stations.add(station_name)
                stations.append(ScrapedStation(
                    name=station_name,
                    line_name=current_line,
                ))

        # 如果没有从表格中提取到，尝试从正文中提取
        if not stations:
            station_pattern = re.compile(r"([一-龥]{2,10}站)")
            for match in station_pattern.finditer(text):
                name = match.group(1)
                if name not in seen_stations and len(name) >= 3:
                    seen_stations.add(name)
                    stations.append(ScrapedStation(name=name))

        return lines, stations

    def _extract_line_from_context(self, table) -> str:
        """从表格上下文中提取线路名"""
        # 检查表格前面的标题
        prev = table.find_previous(["h2", "h3", "h4", "caption"])
        if prev:
            text = prev.get_text(strip=True)
            match = re.search(r"([一-龥]*?\d+号线|环线)", text)
            if match:
                return match.group(1)

        # 检查表格第一行
        first_row = table.find("tr")
        if first_row:
            text = first_row.get_text(strip=True)
            match = re.search(r"([一-龥]*?\d+号线|环线)", text)
            if match:
                return match.group(1)

        return ""

    def _clean_station_name(self, name: str) -> str:
        """清理站点名称"""
        if not name:
            return ""
        # 去除引用标记
        name = re.sub(r"\[\d+\]", "", name)
        # 去除括号内容
        name = re.sub(r"[（(].*?[）)]", "", name)
        name = name.strip()
        # 去除多余空白
        name = re.sub(r"\s+", "", name)
        # 确保以"站"结尾
        if name and not name.endswith("站") and re.search(r"[一-龥]", name):
            name += "站"
        return name
