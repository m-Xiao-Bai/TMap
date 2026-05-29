"""
数据源 — OpenStreetMap Overpass API

基于已有的爬取脚本改造，提供精确的坐标数据。
此数据源已验证可用（49城数据已爬取）。
"""

import re
import logging
import httpx

from app.crawler.sources.base import BaseSource, CrawlResult, ScrapedLine, ScrapedStation

logger = logging.getLogger("tmap-python.crawler.osm")

OVERPASS_URL = "https://overpass-api.de/api/interpreter"

# 查询模板
QUERY_STRICT = """data=[out:json];area[name='{city}']->.a;(
  node['railway'='station']['station'='subway'](area.a);
  node['railway'='station']['station'='light_rail'](area.a);
  node['railway'='station']['station'='monorail'](area.a);
  way['railway'='station']['station'='subway'](area.a);
  way['railway'='station']['station'='light_rail'](area.a);
  way['railway'='station']['station'='monorail'](area.a);
);out body;"""

QUERY_RELAXED = """data=[out:json];area[name='{city}']->.a;(
  node['railway'='station']['subway'='yes'](area.a);
  node['railway'='station']['station'='subway'](area.a);
  node['railway'='station']['station'='light_rail'](area.a);
  node['railway'='station']['station'='monorail'](area.a);
  way['railway'='station']['station'='subway'](area.a);
  way['railway'='station']['station'='light_rail'](area.a);
  way['railway'='station']['station'='monorail'](area.a);
);out body;"""


class OsmSource(BaseSource):
    """OpenStreetMap Overpass API 数据源"""

    @property
    def name(self) -> str:
        return "osm"

    async def crawl(self, city_name: str) -> CrawlResult:
        """从 OSM Overpass API 爬取城市地铁数据"""
        result = CrawlResult(source=self.name, city_name=city_name)

        try:
            # 尝试严格查询
            elements = await self._query(city_name, relaxed=False)
            if not elements:
                # 宽松查询
                elements = await self._query(city_name, relaxed=True)

            if not elements:
                result.success = False
                result.error = f"OSM 中未找到「{city_name}」的地铁站数据"
                return result

            # 提取站点和线路
            stations, lines = self._extract(elements, city_name)
            result.stations = stations
            result.lines = lines

            logger.info(f"[osm] {city_name}: {len(lines)} 条线路, {len(stations)} 个站点")

        except Exception as e:
            result.success = False
            result.error = str(e)
            logger.error(f"[osm] {city_name} 爬取失败: {e}")

        return result

    async def _query(self, city: str, relaxed: bool = False) -> list[dict]:
        """查询 Overpass API"""
        template = QUERY_RELAXED if relaxed else QUERY_STRICT
        payload = template.format(city=city)

        try:
            async with httpx.AsyncClient(timeout=30) as client:
                resp = await client.post(
                    OVERPASS_URL,
                    data={"data": payload},
                    headers={
                        "Content-Type": "application/x-www-form-urlencoded",
                        "User-Agent": "TransitMapBot/1.0",
                    },
                )
                if resp.status_code == 200:
                    return resp.json().get("elements", [])
                logger.warning(f"Overpass API 返回 {resp.status_code}")
                return []
        except Exception as e:
            logger.error(f"Overpass API 查询失败: {e}")
            return []

    def _extract(self, elements: list[dict], city: str) -> tuple[list[ScrapedStation], list[ScrapedLine]]:
        """从 OSM 元素中提取站点和线路"""
        stations = []
        lines_map = {}  # line_name -> ScrapedLine
        seen_keys = set()

        for elem in elements:
            tags = elem.get("tags") or {}

            # 跳过货运站
            if tags.get("usage") == "freight":
                continue

            # 站名
            name = tags.get("name:zh") or tags.get("name") or ""
            if not name:
                continue
            if not name.endswith("站"):
                name += "站"

            # 英文名
            name_en = tags.get("name:en", "")

            # 别称
            alias = tags.get("short_name", "")
            if not alias:
                alt = tags.get("alt_name", "")
                if alt:
                    alias = re.split(r"[;；,，]", alt)[0].strip()

            # 坐标
            lon = elem.get("lon")
            lat = elem.get("lat")
            if lon is None or lat is None:
                # way 类型的坐标需要计算中心点
                if elem.get("type") == "way" and "center" in elem:
                    lon = elem["center"].get("lon")
                    lat = elem["center"].get("lat")
                if lon is None or lat is None:
                    continue

            # 去重
            key = (name, round(float(lon), 6), round(float(lat), 6))
            if key in seen_keys:
                continue
            seen_keys.add(key)

            # 线路信息
            line_name = tags.get("line", "") or tags.get("network", "")
            is_transfer = bool(line_name and re.search(r"[;；,，/]", line_name))

            # 如果有多条线路，拆分
            line_names = []
            if line_name:
                line_names = re.split(r"[;；,，/]", line_name)
                line_names = [ln.strip() for ln in line_names if ln.strip()]

            # 注册线路
            for ln in line_names:
                if ln not in lines_map:
                    lines_map[ln] = ScrapedLine(name=ln)
                if name not in lines_map[ln].stations:
                    lines_map[ln].stations.append(name)

            # osmid
            osmid = elem.get("id", 0)

            stations.append(ScrapedStation(
                name=name,
                name_en=name_en,
                alias=alias,
                line_name=line_name,
                is_transfer=is_transfer,
                lat=round(float(lat), 6),
                lng=round(float(lon), 6),
                osmid=osmid,
            ))

        return stations, list(lines_map.values())
