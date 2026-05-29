"""
爬虫管线 — Step 4: 数据质检

检查项：
1. 坐标范围：是否在中国境内
2. 重复检测：同名站点、同坐标站点
3. 相邻站点距离合理性
"""

import math
import logging
from app.crawler.sources.comparator import MergedStation, MergedLine
from app.crawler.progress_tracker import progress_tracker

logger = logging.getLogger("tmap-python.crawler.pipeline.quality_checker")

# 中国境内大致范围
CHINA_LAT_RANGE = (3.0, 53.0)
CHINA_LNG_RANGE = (73.0, 135.0)

# 相邻站点合理距离范围（米）
MIN_STATION_DISTANCE = 200
MAX_STATION_DISTANCE = 5000


def haversine(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """计算两点间的距离（米）"""
    R = 6371000  # 地球半径（米）
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlam = math.radians(lng2 - lng1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlam / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


class QualityReport:
    """质检报告"""
    def __init__(self):
        self.issues: list[dict] = []
        self.stats: dict = {}

    def add_issue(self, level: str, category: str, message: str, station_name: str = ""):
        self.issues.append({
            "level": level,  # "error" | "warning" | "info"
            "category": category,
            "message": message,
            "station_name": station_name,
        })

    @property
    def error_count(self):
        return sum(1 for i in self.issues if i["level"] == "error")

    @property
    def warning_count(self):
        return sum(1 for i in self.issues if i["level"] == "warning")

    @property
    def quality_score(self) -> float:
        """质量评分 0-1"""
        if not self.stats.get("total_stations"):
            return 0.0
        error_rate = self.error_count / self.stats["total_stations"]
        warning_rate = self.warning_count / self.stats["total_stations"]
        return max(0.0, 1.0 - error_rate * 0.5 - warning_rate * 0.1)


async def check_quality(
    task_id: str,
    city_name: str,
    stations: list[MergedStation],
    lines: list[MergedLine],
) -> QualityReport:
    """
    执行数据质检。

    Args:
        task_id: 任务 ID
        city_name: 城市名称
        stations: 站点列表
        lines: 线路列表

    Returns:
        QualityReport 质检报告
    """
    report = QualityReport()
    report.stats = {
        "total_stations": len(stations),
        "total_lines": len(lines),
        "with_coords": sum(1 for s in stations if s.lat != 0 and s.lng != 0),
        "with_address": sum(1 for s in stations if s.address),
    }

    # 1. 坐标范围检查
    for s in stations:
        if s.lat == 0 and s.lng == 0:
            report.add_issue("warning", "no_coords", f"站点缺少坐标数据", s.name)
            continue
        if not (CHINA_LAT_RANGE[0] <= s.lat <= CHINA_LAT_RANGE[1]):
            report.add_issue("error", "out_of_range", f"纬度 {s.lat} 超出中国范围", s.name)
        if not (CHINA_LNG_RANGE[0] <= s.lng <= CHINA_LNG_RANGE[1]):
            report.add_issue("error", "out_of_range", f"经度 {s.lng} 超出中国范围", s.name)

    # 2. 重复检测
    seen_names: dict[str, list[str]] = {}
    seen_coords: dict[str, list[str]] = {}
    for s in stations:
        # 同名站点
        name_key = s.name.rstrip("站")
        if name_key not in seen_names:
            seen_names[name_key] = []
        seen_names[name_key].append(s.name)

        # 同坐标站点（精确到小数点后 4 位，约 11 米精度）
        if s.lat != 0 and s.lng != 0:
            coord_key = f"{round(s.lat, 4)},{round(s.lng, 4)}"
            if coord_key not in seen_coords:
                seen_coords[coord_key] = []
            seen_coords[coord_key].append(s.name)

    for name, station_list in seen_names.items():
        if len(station_list) > 1:
            report.add_issue("warning", "duplicate_name",
                           f"同名站点: {', '.join(station_list)}", name)

    for coord, station_list in seen_coords.items():
        if len(station_list) > 1:
            report.add_issue("warning", "duplicate_coords",
                           f"同坐标站点: {', '.join(station_list)} ({coord})")

    # 3. 线路站点序列检查
    for line in lines:
        if len(line.stations) < 2:
            report.add_issue("info", "short_line",
                           f"线路站点少于 2 个: {line.name}")
            continue

        # 检查相邻站点距离
        station_map = {s.name: s for s in stations}
        for i in range(len(line.stations) - 1):
            s1_name = line.stations[i]
            s2_name = line.stations[i + 1]
            s1 = station_map.get(s1_name)
            s2 = station_map.get(s2_name)
            if not s1 or not s2:
                continue
            if s1.lat == 0 or s2.lat == 0:
                continue
            dist = haversine(s1.lat, s1.lng, s2.lat, s2.lng)
            if dist < MIN_STATION_DISTANCE:
                report.add_issue("warning", "too_close",
                               f"相邻站点过近 ({dist:.0f}m): {s1_name} - {s2_name}")
            elif dist > MAX_STATION_DISTANCE:
                report.add_issue("warning", "too_far",
                               f"相邻站点过远 ({dist:.0f}m): {s1_name} - {s2_name}")

    # 4. confidence=low 统计
    low_confidence = [s for s in stations if s.confidence == "low"]
    if low_confidence:
        report.add_issue("info", "low_confidence",
                        f"{len(low_confidence)} 个站点置信度为 low，建议人工审核")

    report.stats["errors"] = report.error_count
    report.stats["warnings"] = report.warning_count
    report.stats["quality_score"] = report.quality_score

    logger.info(
        f"质检完成: {report.error_count} 错误, {report.warning_count} 警告, "
        f"评分 {report.quality_score:.2f}"
    )

    return report
