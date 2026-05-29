"""
爬虫数据源 — 多源对比器

将 Wikipedia、百度百科、OSM 三个数据源的结果进行对比合并。
策略：
- 三个源都有的站点 → confidence=high
- 两个源有 → confidence=medium
- 只有一个源有 → confidence=low
- 冲突时以 OSM 为主（有精确坐标），Wikipedia 为辅
"""

import re
import logging
from dataclasses import dataclass, field
from app.crawler.sources.base import CrawlResult, ScrapedLine, ScrapedStation

logger = logging.getLogger("tmap-python.crawler.comparator")


@dataclass
class MergedStation:
    """合并后的站点"""
    name: str
    name_en: str = ""
    alias: str = ""
    line_names: list[str] = field(default_factory=list)
    lat: float = 0.0
    lng: float = 0.0
    osmid: int = 0
    confidence: str = "medium"  # high/medium/low
    sources: list[str] = field(default_factory=list)  # 来源列表
    address: str = ""


@dataclass
class MergedLine:
    """合并后的线路"""
    name: str
    name_en: str = ""
    color: str = ""
    stations: list[str] = field(default_factory=list)  # 站点名列表（按顺序）


@dataclass
class ComparisonResult:
    """对比结果"""
    city_name: str
    merged_lines: list[MergedLine] = field(default_factory=list)
    merged_stations: list[MergedStation] = field(default_factory=list)
    source_stats: dict = field(default_factory=dict)  # 每个源的统计
    confidence_summary: dict = field(default_factory=dict)  # {"high": N, "medium": N, "low": N}


def normalize_name(name: str) -> str:
    """标准化名称用于对比"""
    if not name:
        return ""
    # 去掉"站"后缀
    name = re.sub(r"站$", "", name)
    # 去掉括号
    name = re.sub(r"[（(].*?[）)]", "", name)
    # 去掉空白
    name = re.sub(r"\s+", "", name)
    return name.lower()


class SourceComparator:
    """多源对比器"""

    def compare(self, results: list[CrawlResult]) -> ComparisonResult:
        """
        对比多个数据源的结果，生成合并数据。

        Args:
            results: 各数据源的爬取结果（失败的会被过滤）

        Returns:
            ComparisonResult 合并后的数据
        """
        city_name = ""
        valid_results = []
        for r in results:
            if r.city_name:
                city_name = r.city_name
            if r.success and r.stations:
                valid_results.append(r)

        if not valid_results:
            return ComparisonResult(city_name=city_name)

        # 统计各源数据量
        source_stats = {}
        for r in valid_results:
            source_stats[r.source] = {
                "lines": len(r.lines),
                "stations": len(r.stations),
            }

        # 合并站点
        merged_stations = self._merge_stations(valid_results)

        # 合并线路
        merged_lines = self._merge_lines(valid_results)

        # 置信度统计
        confidence_summary = {"high": 0, "medium": 0, "low": 0}
        for s in merged_stations:
            confidence_summary[s.confidence] = confidence_summary.get(s.confidence, 0) + 1

        logger.info(
            f"对比完成: {len(merged_stations)} 个站点, "
            f"high={confidence_summary['high']}, "
            f"medium={confidence_summary['medium']}, "
            f"low={confidence_summary['low']}"
        )

        return ComparisonResult(
            city_name=city_name,
            merged_lines=merged_lines,
            merged_stations=merged_stations,
            source_stats=source_stats,
            confidence_summary=confidence_summary,
        )

    def _merge_stations(self, results: list[CrawlResult]) -> list[MergedStation]:
        """合并站点数据"""
        # 按标准化名称分组
        name_groups: dict[str, list[tuple[str, ScrapedStation]]] = {}

        for r in results:
            for station in r.stations:
                key = normalize_name(station.name)
                if not key:
                    continue
                if key not in name_groups:
                    name_groups[key] = []
                name_groups[key].append((r.source, station))

        merged = []
        for key, group in name_groups.items():
            sources = [g[0] for g in group]
            stations = [g[1] for g in group]

            # 选择最佳数据
            best = self._pick_best_station(stations, sources)

            # 确定置信度
            unique_sources = list(set(sources))
            if len(unique_sources) >= 3:
                confidence = "high"
            elif len(unique_sources) >= 2:
                confidence = "medium"
            else:
                # 只有一个源，OSM 数据因为有坐标所以可信度稍高
                if "osm" in unique_sources and best.lat != 0:
                    confidence = "medium"
                else:
                    confidence = "low"

            merged.append(MergedStation(
                name=best.name,
                name_en=best.name_en or "",
                alias=best.alias or "",
                line_names=self._merge_line_names(stations),
                lat=best.lat,
                lng=best.lng,
                osmid=best.osmid,
                confidence=confidence,
                sources=unique_sources,
            ))

        # 按名称排序
        merged.sort(key=lambda s: s.name)
        return merged

    def _merge_lines(self, results: list[CrawlResult]) -> list[MergedLine]:
        """合并线路数据"""
        lines_map: dict[str, MergedLine] = {}

        # 优先使用 OSM 的线路数据（有完整站点列表）
        sorted_results = sorted(results, key=lambda r: 0 if r.source == "osm" else 1)

        for r in sorted_results:
            for line in r.lines:
                key = normalize_name(line.name)
                if not key:
                    continue
                if key not in lines_map:
                    lines_map[key] = MergedLine(
                        name=line.name,
                        name_en=line.name_en,
                        color=line.color,
                        stations=line.stations,
                    )
                else:
                    # 补充站点列表
                    existing = lines_map[key]
                    for s in line.stations:
                        if normalize_name(s) not in [normalize_name(es) for es in existing.stations]:
                            existing.stations.append(s)
                    if not existing.color and line.color:
                        existing.color = line.color

        return list(lines_map.values())

    def _pick_best_station(self, stations: list[ScrapedStation], sources: list[str]) -> ScrapedStation:
        """从多个同名站点中选择最佳数据"""
        # 优先选择有坐标的
        with_coords = [s for s in stations if s.lat != 0 and s.lng != 0]
        if with_coords:
            # 优先 OSM（坐标最精确）
            osm_stations = [s for s in with_coords if s.osmid != 0]
            if osm_stations:
                return osm_stations[0]
            return with_coords[0]

        # 都没有坐标，选择信息最丰富的
        return max(stations, key=lambda s: len(s.name_en) + len(s.alias) + len(s.line_name))

    def _merge_line_names(self, stations: list[ScrapedStation]) -> list[str]:
        """合并所有站点的线路名"""
        names = set()
        for s in stations:
            if s.line_name:
                # 拆分多线路
                parts = re.split(r"[;；,，/]", s.line_name)
                for p in parts:
                    p = p.strip()
                    if p:
                        names.add(p)
        return sorted(names)
