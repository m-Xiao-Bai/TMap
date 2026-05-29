"""
爬虫管线 — Step 5: 增量写入 DB

增量策略：
- 查询 DB 已有该城市的线路和站点
- 对比名称
- 新增站点 → INSERT
- 已存在 → SKIP（不覆盖人工数据）
- confidence=low 的站点写入 station_review 表（待审核）
"""

import json
import logging
from sqlalchemy import text
from app.db.connection import db_manager
from app.crawler.sources.comparator import MergedStation, MergedLine, ComparisonResult
from app.crawler.progress_tracker import progress_tracker
from app.crawler.pipeline.quality_checker import QualityReport

logger = logging.getLogger("tmap-python.crawler.pipeline.db_writer")


async def write_to_db(
    task_id: str,
    country_id: int,
    country_name: str,
    city_id: int,
    city_name: str,
    comparison: ComparisonResult,
    quality_report: QualityReport,
) -> dict:
    """
    增量写入数据库。

    Args:
        task_id: 任务 ID
        country_id/country_name: 国家信息
        city_id/city_name: 城市信息
        comparison: 对比结果
        quality_report: 质检报告

    Returns:
        写入统计 {"lines_inserted": N, "stations_inserted": N, ...}
    """
    stats = {
        "lines_inserted": 0,
        "lines_updated": 0,
        "lines_skipped": 0,
        "stations_inserted": 0,
        "stations_updated": 0,
        "stations_skipped": 0,
        "stations_pending_review": 0,
    }

    await progress_tracker.update(
        task_id, 80, "db_writing",
        f"开始写入数据库: {len(comparison.merged_lines)} 线路, {len(comparison.merged_stations)} 站点",
    )

    # 1. 写入线路
    line_name_to_id = {}
    existing_lines = await _get_existing_lines(city_id)
    existing_line_names = {l["line_name"]: l["id"] for l in existing_lines}

    for line in comparison.merged_lines:
        if line.name in existing_line_names:
            line_name_to_id[line.name] = existing_line_names[line.name]
            stats["lines_skipped"] += 1
            continue

        try:
            line_id = await _insert_line(
                country_id, country_name, city_id, city_name, line,
            )
            line_name_to_id[line.name] = line_id
            stats["lines_inserted"] += 1
            logger.info(f"新增线路: {line.name} (ID={line_id})")
        except Exception as e:
            logger.error(f"写入线路失败 {line.name}: {e}")

    # 2. 写入站点
    existing_stations = await _get_existing_stations(city_id)
    existing_station_keys = set()
    for s in existing_stations:
        key = _station_key(s["station_name"])
        existing_station_keys.add(key)

    for station in comparison.merged_stations:
        key = _station_key(station.name)

        if key in existing_station_keys:
            stats["stations_skipped"] += 1
            continue

        # confidence=low 的写入审核表
        if station.confidence == "low":
            await _insert_review(task_id, city_name, station)
            stats["stations_pending_review"] += 1
            continue

        try:
            # 构建 line_ids 和 line_names
            line_ids = []
            line_names = []
            for ln in station.line_names:
                if ln in line_name_to_id:
                    line_ids.append(line_name_to_id[ln])
                    line_names.append(ln)

            station_id = await _insert_station(
                country_id, country_name, city_id, city_name,
                station, line_ids, line_names,
            )
            stats["stations_inserted"] += 1
            logger.debug(f"新增站点: {station.name} (ID={station_id})")
        except Exception as e:
            logger.error(f"写入站点失败 {station.name}: {e}")

    # 更新任务记录中的城市 ID
    await _update_task_city(task_id, city_id)

    await progress_tracker.update(
        task_id, 90, "db_writing",
        f"数据库写入完成: 线路 +{stats['lines_inserted']}, "
        f"站点 +{stats['stations_inserted']}, "
        f"跳过 {stats['stations_skipped']}, "
        f"待审核 {stats['stations_pending_review']}",
    )

    return stats


async def _get_existing_lines(city_id: int) -> list[dict]:
    """获取城市已有线路"""
    async with db_manager.get_session() as session:
        result = await session.execute(
            text("SELECT id, line_name FROM metro_line WHERE city_id = :city_id"),
            {"city_id": city_id},
        )
        return [dict(row) for row in result.mappings().all()]


async def _get_existing_stations(city_id: int) -> list[dict]:
    """获取城市已有站点"""
    async with db_manager.get_session() as session:
        result = await session.execute(
            text("SELECT id, station_name FROM metro_station WHERE city_id = :city_id"),
            {"city_id": city_id},
        )
        return [dict(row) for row in result.mappings().all()]


async def _insert_line(
    country_id: int, country_name: str,
    city_id: int, city_name: str,
    line: MergedLine,
) -> int:
    """插入线路"""
    import re
    # 提取线路编号（如 "1号线" → "1"）
    line_no = re.sub(r"[^0-9a-zA-Z]", "", line.name) or line.name
    async with db_manager.get_session() as session:
        result = await session.execute(
            text("""INSERT INTO metro_line
                    (country_id, country_name, city_id, city_name,
                     line_name, line_no, line_color, status_code, created_at, updated_at)
                    VALUES (:country_id, :country_name, :city_id, :city_name,
                            :line_name, :line_no, :line_color, 1, NOW(), NOW())"""),
            {
                "country_id": country_id,
                "country_name": country_name,
                "city_id": city_id,
                "city_name": city_name,
                "line_name": line.name,
                "line_no": line_no,
                "line_color": line.color or "#000000",
            },
        )
        await session.commit()
        return result.lastrowid


async def _insert_station(
    country_id: int, country_name: str,
    city_id: int, city_name: str,
    station: MergedStation,
    line_ids: list[int],
    line_names: list[str],
) -> int:
    """插入站点"""
    # 标记数据来源
    extra = json.dumps({
        "source": "crawler",
        "confidence": station.confidence,
        "sources": station.sources,
    }, ensure_ascii=False)
    async with db_manager.get_session() as session:
        result = await session.execute(
            text("""INSERT INTO metro_station
                    (country_id, country_name, city_id, city_name,
                     station_name, station_name_en, station_alias,
                     line_ids, line_names,
                     longitude, latitude,
                     osmid, is_transfer, status_code, extra, created_at, updated_at)
                    VALUES (:country_id, :country_name, :city_id, :city_name,
                            :station_name, :station_name_en, :station_alias,
                            :line_ids, :line_names,
                            :longitude, :latitude,
                            :osmid, :is_transfer, 1, :extra, NOW(), NOW())"""),
            {
                "country_id": country_id,
                "country_name": country_name,
                "city_id": city_id,
                "city_name": city_name,
                "station_name": station.name,
                "station_name_en": station.name_en or "",
                "station_alias": station.alias or "",
                "line_ids": json.dumps(line_ids),
                "line_names": json.dumps(line_names, ensure_ascii=False),
                "longitude": station.lng,
                "latitude": station.lat,
                "osmid": station.osmid or 0,
                "is_transfer": 1 if len(line_ids) > 1 else 0,
                "extra": extra,
            },
        )
        await session.commit()
        return result.lastrowid


async def _insert_review(task_id: str, city_name: str, station: MergedStation):
    """写入审核表"""
    async with db_manager.get_session() as session:
        await session.execute(
            text("""INSERT INTO station_review
                    (task_id, city_name, station_name, line_name,
                     scraped_address, scraped_lat, scraped_lng,
                     confidence, review_status, created_at)
                    VALUES (:task_id, :city_name, :station_name, :line_name,
                            :scraped_address, :scraped_lat, :scraped_lng,
                            :confidence, 'pending', NOW())"""),
            {
                "task_id": task_id,
                "city_name": city_name,
                "station_name": station.name,
                "line_name": ",".join(station.line_names),
                "scraped_address": station.address,
                "scraped_lat": station.lat if station.lat != 0 else None,
                "scraped_lng": station.lng if station.lng != 0 else None,
                "confidence": station.confidence,
            },
        )
        await session.commit()


async def _update_task_city(task_id: str, city_id: int):
    """更新任务关联的城市 ID"""
    async with db_manager.get_session() as session:
        await session.execute(
            text("UPDATE crawler_task SET city_id = :city_id WHERE task_id = :task_id"),
            {"task_id": task_id, "city_id": city_id},
        )
        await session.commit()


async def update_city_stats(city_id: int):
    """
    更新城市的 metro_count 和 metro_line_count。
    从实际插入的站点/线路数据统计。
    """
    async with db_manager.get_session() as session:
        # 统计站点数
        result = await session.execute(
            text("SELECT COUNT(*) FROM metro_station WHERE city_id = :city_id"),
            {"city_id": city_id},
        )
        station_count = result.scalar() or 0

        # 统计线路数
        result = await session.execute(
            text("SELECT COUNT(*) FROM metro_line WHERE city_id = :city_id"),
            {"city_id": city_id},
        )
        line_count = result.scalar() or 0

        # 更新城市记录
        await session.execute(
            text("""UPDATE city
                    SET metro_count = :station_count,
                        metro_line_count = :line_count,
                        updated_at = NOW()
                    WHERE id = :city_id"""),
            {"city_id": city_id, "station_count": station_count, "line_count": line_count},
        )
        await session.commit()
        logger.info(f"更新城市统计: city_id={city_id}, 站点={station_count}, 线路={line_count}")


def _station_key(name: str) -> str:
    """生成站点去重键"""
    import re
    key = name.rstrip("站")
    key = re.sub(r"[（(].*?[）)]", "", key)
    return key.lower()
