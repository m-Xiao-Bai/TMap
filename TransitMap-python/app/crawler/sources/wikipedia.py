"""
数据源 — 维基百科

从中文维基百科解析城市地铁线路和站点数据。
目标页面格式: https://zh.wikipedia.org/wiki/{城市}地铁
"""

import re
import logging
from bs4 import BeautifulSoup
import httpx

from app.crawler.sources.base import BaseSource, CrawlResult, ScrapedLine, ScrapedStation

logger = logging.getLogger("tmap-python.crawler.wikipedia")

# 维基百科中文域名
WIKI_BASE = "https://zh.wikipedia.org/wiki/"


class WikipediaSource(BaseSource):
    """维基百科数据源"""

    @property
    def name(self) -> str:
        return "wikipedia"

    async def crawl(self, city_name: str) -> CrawlResult:
        """从维基百科爬取城市地铁数据"""
        result = CrawlResult(source=self.name, city_name=city_name)

        try:
            # 尝试多个可能的页面标题
            titles = self._generate_titles(city_name)
            html = None
            for title in titles:
                html = await self._fetch_page(title)
                if html:
                    break

            if not html:
                result.success = False
                result.error = f"未找到「{city_name}」的维基百科地铁页面"
                return result

            soup = BeautifulSoup(html, "lxml")

            # 解析线路信息
            lines = self._parse_lines(soup, city_name)
            result.lines = lines

            # 解析站点信息
            stations = self._parse_stations(soup, city_name)
            result.stations = stations

            logger.info(f"[wikipedia] {city_name}: {len(lines)} 条线路, {len(stations)} 个站点")

        except Exception as e:
            result.success = False
            result.error = str(e)
            logger.error(f"[wikipedia] {city_name} 爬取失败: {e}")

        return result

    def _generate_titles(self, city_name: str) -> list[str]:
        """生成可能的维基页面标题"""
        # 去掉"市"后缀
        short = city_name.replace("市", "").replace("地区", "")
        return [
            f"{short}地铁",
            f"{short}轨道交通",
            f"{short}市地铁",
            f"{short}城市轨道交通",
        ]

    async def _fetch_page(self, title: str) -> str | None:
        """获取维基百科页面 HTML"""
        url = WIKI_BASE + title
        try:
            async with httpx.AsyncClient(timeout=15, follow_redirects=True) as client:
                resp = await client.get(url, headers={
                    "User-Agent": "TransitMapBot/1.0 (transit-map project)",
                    "Accept-Language": "zh-CN,zh;q=0.9",
                })
                if resp.status_code == 200:
                    return resp.text
                return None
        except Exception as e:
            logger.debug(f"获取 {url} 失败: {e}")
            return None

    def _parse_lines(self, soup: BeautifulSoup, city_name: str) -> list[ScrapedLine]:
        """从页面中解析线路列表"""
        lines = []
        seen_names = set()

        # 方法1: 查找 "线路" 相关表格
        for table in soup.find_all("table", class_="wikitable"):
            rows = table.find_all("tr")
            if not rows:
                continue

            # 检查表头是否包含"线路"相关字段
            header = rows[0]
            header_text = header.get_text()
            if "线路" not in header_text and "路线" not in header_text:
                continue

            for row in rows[1:]:
                cells = row.find_all(["td", "th"])
                if len(cells) < 2:
                    continue

                line_name = cells[0].get_text(strip=True)
                if not line_name or line_name in seen_names:
                    continue

                # 清理线路名
                line_name = self._clean_line_name(line_name)
                if not line_name:
                    continue

                seen_names.add(line_name)

                # 尝试提取颜色
                color = self._extract_color(row)

                lines.append(ScrapedLine(name=line_name, color=color))

        # 方法2: 从导航模板中提取
        for navbox in soup.find_all("div", class_="navbox"):
            for link in navbox.find_all("a"):
                text = link.get_text(strip=True)
                if re.search(r"[一二三四五六七八九十\d]+号线|环线|[东西南北]延", text):
                    name = self._clean_line_name(text)
                    if name and name not in seen_names:
                        seen_names.add(name)
                        lines.append(ScrapedLine(name=name))

        return lines

    def _parse_stations(self, soup: BeautifulSoup, city_name: str) -> list[ScrapedStation]:
        """从页面中解析站点列表"""
        stations = []
        seen_names = set()

        # 查找站点表格（通常包含"站名"列）
        for table in soup.find_all("table", class_="wikitable"):
            rows = table.find_all("tr")
            if not rows:
                continue

            header_text = rows[0].get_text()
            if "站" not in header_text:
                continue

            # 确定列索引
            header_cells = rows[0].find_all(["th", "td"])
            col_map = self._detect_columns(header_cells)

            current_line = ""
            for row in rows[1:]:
                cells = row.find_all(["td", "th"])
                if len(cells) < 2:
                    continue

                station_name = ""
                line_name = ""
                lat, lng = 0.0, 0.0

                # 按列映射提取
                if "站名" in col_map:
                    idx = col_map["站名"]
                    if idx < len(cells):
                        station_name = cells[idx].get_text(strip=True)
                elif len(cells) > 0:
                    station_name = cells[0].get_text(strip=True)

                if "线路" in col_map:
                    idx = col_map["线路"]
                    if idx < len(cells):
                        line_name = cells[idx].get_text(strip=True)
                        if line_name:
                            current_line = line_name
                if not line_name:
                    line_name = current_line

                # 尝试从链接中提取站名
                if not station_name or len(station_name) < 2:
                    for cell in cells:
                        link = cell.find("a")
                        if link and "站" in link.get_text():
                            station_name = link.get_text(strip=True)
                            break

                if not station_name or station_name in seen_names:
                    continue

                # 清理站名
                station_name = self._clean_station_name(station_name)
                if not station_name:
                    continue

                seen_names.add(station_name)

                stations.append(ScrapedStation(
                    name=station_name,
                    line_name=line_name,
                    lat=lat,
                    lng=lng,
                ))

        return stations

    def _detect_columns(self, header_cells) -> dict:
        """检测列索引映射"""
        col_map = {}
        keywords = {
            "站名": ["站名", "车站", "站"],
            "线路": ["线路", "路线", "所属线路"],
            "坐标": ["坐标", "位置"],
        }
        for i, cell in enumerate(header_cells):
            text = cell.get_text(strip=True)
            for key, kws in keywords.items():
                if any(kw in text for kw in kws):
                    col_map[key] = i
                    break
        return col_map

    def _clean_line_name(self, name: str) -> str:
        """清理线路名称"""
        if not name:
            return ""
        # 去除括号内容
        name = re.sub(r"[（(].*?[）)]", "", name)
        # 去除多余空白
        name = name.strip()
        # 确保包含"线"或"号线"
        if not re.search(r"线|号线|环线", name):
            if re.search(r"\d|[一二三四五六七八九十]", name):
                name += "号线"
        return name

    def _clean_station_name(self, name: str) -> str:
        """清理站点名称"""
        if not name:
            return ""
        # 去除引用标记 [1] [2] 等
        name = re.sub(r"\[\d+\]", "", name)
        # 去除括号内容
        name = re.sub(r"[（(].*?[）)]", "", name)
        name = name.strip()
        # 确保以"站"结尾
        if name and not name.endswith("站"):
            name += "站"
        return name

    def _extract_color(self, row) -> str:
        """从表格行中提取线路颜色"""
        # 尝试从 style 属性中提取背景色
        for cell in row.find_all(["td", "th"]):
            style = cell.get("style", "")
            match = re.search(r"background:\s*(#[0-9a-fA-F]{6})", style)
            if match:
                return match.group(1)
        return ""
