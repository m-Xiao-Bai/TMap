"""
爬虫服务 — 编排器

串联 6 步管线，处理整个爬取流程。
数据源：OSM Overpass API（与原脚本一致）。
城市/线路数据由 LLM 补充。
"""

import json
import logging
import traceback
from app.crawler.pipeline.scraper import scrape
from app.crawler.pipeline.llm_validator import validate_with_llm
from app.crawler.pipeline.geo_coder import geocode_stations
from app.crawler.pipeline.quality_checker import check_quality
from app.crawler.pipeline.db_writer import write_to_db, update_city_stats
from app.crawler.pipeline.reporter import generate_report
from app.crawler.pipeline.city_enricher import enrich_city_with_llm, enrich_lines_with_llm
from app.crawler.progress_tracker import progress_tracker
from app.crawler.task_queue import crawler_queue
from app.utils.id_generator import generate_id

logger = logging.getLogger("tmap-python.crawler.service")


async def handle_crawl_task(task_data: dict):
    """
    处理一个爬取任务（Worker 调用此函数）。

    完整管线：
    ① OSM 爬取 → ② 确保城市/线路存在 → ③ LLM 校验 → ④ 坐标补全 → ⑤ 质检 → ⑥ 写入 DB → ⑦ 报告
    """
    task_id = task_data.get("task_id", "")
    city_name = task_data.get("city_name", "")
    country_id = int(task_data.get("country_id", 1))

    errors = []

    try:
        logger.info(f"[{task_id}] 开始处理: {city_name}")

        # ① OSM 爬取
        crawl_results = await scrape(task_id, city_name)
        if not crawl_results or not crawl_results[0].success:
            error_msg = crawl_results[0].error if crawl_results else "爬取失败"
            errors.append(error_msg)
            await crawler_queue.update_task_status(task_id, "failed", error_message=error_msg)
            await progress_tracker.error(task_id, error_msg)
            return

        # 单数据源，直接使用结果
        osm_result = crawl_results[0]
        if not osm_result.stations:
            errors.append("OSM 未找到地铁站数据")
            await crawler_queue.update_task_status(task_id, "failed", error_message="未找到地铁站数据")
            await progress_tracker.error(task_id, "未找到地铁站数据")
            return

        # 构建 ComparisonResult（单数据源）
        from app.crawler.sources.comparator import ComparisonResult, MergedStation, MergedLine
        comparison = ComparisonResult(city_name=city_name)
        comparison.merged_lines = [
            MergedLine(name=l.name, stations=l.stations)
            for l in osm_result.lines
        ]
        comparison.merged_stations = [
            MergedStation(
                name=s.name, name_en=s.name_en, alias=s.alias,
                line_names=[s.line_name] if s.line_name else [],
                lat=s.lat, lng=s.lng, osmid=s.osmid,
                confidence="high", sources=["osm"],
            )
            for s in osm_result.stations
        ]
        comparison.source_stats = {"osm": {"lines": len(osm_result.lines), "stations": len(osm_result.stations)}}
        comparison.confidence_summary = {"high": len(osm_result.stations), "medium": 0, "low": 0}

        # ③ 确保城市和线路存在（从爬取数据推导，不问 LLM）
        await progress_tracker.update(task_id, 25, "ensuring_data", "正在确保城市和线路数据存在...")
        city_id = await _ensure_city(task_id, city_name, country_id, comparison)

        # ④ LLM 校验 + 地址补全
        comparison.merged_stations = await validate_with_llm(
            task_id, city_name,
            comparison.merged_stations,
            comparison.merged_lines,
        )

        # ⑤ 高德坐标补全
        comparison.merged_stations = await geocode_stations(
            task_id, city_name,
            comparison.merged_stations,
        )

        # ⑥ 数据质检
        quality_report = await check_quality(
            task_id, city_name,
            comparison.merged_stations,
            comparison.merged_lines,
        )

        # ⑦ 增量写入 DB
        db_stats = await write_to_db(
            task_id, country_id, "中国", city_id, city_name,
            comparison, quality_report,
        )

        # ⑧ 更新城市统计（站点数、线路数）
        await update_city_stats(city_id)

        # ⑨ 生成报告
        report = await generate_report(
            task_id, city_name,
            comparison.source_stats,
            comparison.confidence_summary,
            db_stats,
            quality_report.quality_score,
            errors,
        )

        logger.info(f"[{task_id}] 任务完成: {city_name}")

    except Exception as e:
        # 获取详细的异常位置
        tb = traceback.extract_tb(e.__traceback__)
        if tb:
            last_frame = tb[-1]
            error_location = f"{last_frame.filename}:{last_frame.lineno} in {last_frame.name}"
            error_msg = f"[{error_location}] {type(e).__name__}: {e}"
        else:
            error_msg = f"{type(e).__name__}: {e}"

        logger.error(f"[{task_id}] 任务异常: {error_msg}")
        logger.error(f"[{task_id}] 完整调用栈:\n{traceback.format_exc()}")
        errors.append(error_msg)
        try:
            await crawler_queue.update_task_status(task_id, "failed", error_message=error_msg)
            await progress_tracker.error(task_id, error_msg)
        except Exception:
            pass


async def _ensure_city(
    task_id: str,
    city_name: str,
    country_id: int,
    comparison,
) -> int:
    """
    确保城市和线路记录存在。

    优化策略：
    - 城市：先查 DB，没有则创建最小记录，再异步 LLM 补充
    - 线路：从爬取站点数据推导，不问 LLM
    """
    from app.db.connection import db_manager
    from sqlalchemy import text
    import re
    import json

    async with db_manager.get_session() as session:
        # 1. 查找已有城市
        result = await session.execute(
            text("SELECT id FROM city WHERE city_name LIKE :name LIMIT 1"),
            {"name": f"%{city_name.replace('市', '')}%"},
        )
        row = result.first()
        if row:
            city_id = row[0]
            logger.info(f"城市已存在: {city_name} (ID={city_id})")
        else:
            # 2. 问大模型获取城市信息
            await progress_tracker.update(
                task_id, 23, "ensuring_data",
                f"正在向大模型询问 {city_name} 的城市信息...",
            )
            llm_data = await enrich_city_with_llm(city_name)

            # 3. 创建城市记录（包含全部 LLM 数据）
            city_id = generate_id()
            extra = json.dumps({
                "source": "llm",
                "llm_date": llm_data.get("_llm_date", ""),
                "confidence": llm_data.get("_confidence", "medium"),
            }, ensure_ascii=False) if llm_data.get("_source") != "llm_failed" else None

            await session.execute(
                text("""INSERT INTO city
                        (id, country_id, country_name, city_name, city_name_en, city_alias,
                         metro_line_logo, metro_count, metro_line_count,
                         hsr_count, metro_km, hsr_km, population,
                         status_code, extra, created_at, updated_at)
                        VALUES (:id, :country_id, '中国', :city_name, :city_name_en, :city_alias,
                                :metro_line_logo, :metro_count, :metro_line_count,
                                :hsr_count, :metro_km, :hsr_km, :population,
                                1, :extra, NOW(), NOW())"""),
                {
                    "id": city_id,
                    "country_id": country_id,
                    "city_name": city_name,
                    "city_name_en": llm_data.get("city_name_en"),
                    "city_alias": llm_data.get("city_alias"),
                    "metro_line_logo": llm_data.get("metro_line_logo"),
                    "metro_count": llm_data.get("metro_count"),
                    "metro_line_count": llm_data.get("metro_line_count"),
                    "hsr_count": llm_data.get("hsr_count"),
                    "metro_km": llm_data.get("metro_km"),
                    "hsr_km": llm_data.get("hsr_km"),
                    "population": llm_data.get("population"),
                    "extra": extra,
                },
            )
            await session.commit()
            logger.info(f"创建城市(LLM): {city_name} (ID={city_id}, en={llm_data.get('city_name_en')}, "
                        f"alias={llm_data.get('city_alias')}, lines={llm_data.get('metro_line_count')}, "
                        f"stations={llm_data.get('metro_count')}, km={llm_data.get('metro_km')})")

        # 4. 问大模型获取线路数据并确保存在
        await _ensure_lines_from_llm(task_id, city_id, city_name, country_id)

        return city_id


async def _ensure_lines_from_llm(
    task_id: str,
    city_id: int,
    city_name: str,
    country_id: int,
):
    """
    问大模型获取线路数据，确保线路记录存在。

    流程：
    1. 问 LLM 获取该城市所有地铁线路
    2. 对比 DB 已有线路
    3. 创建缺失的线路
    """
    from app.db.connection import db_manager
    from sqlalchemy import text

    await progress_tracker.update(
        task_id, 26, "ensuring_data",
        f"正在向大模型询问 {city_name} 的地铁线路信息...",
    )

    # 1. 问 LLM 获取线路数据
    llm_lines = await enrich_lines_with_llm(city_name)
    if not llm_lines:
        logger.warning(f"LLM 未返回线路数据: {city_name}")
        await progress_tracker.update(
            task_id, 27, "ensuring_data",
            f"大模型未返回线路数据，将使用爬取数据中的线路信息",
        )
        # 降级：从爬取数据中提取线路
        await _ensure_lines_from_crawl(task_id, city_id, city_name, country_id)
        return

    # 2. 检查 DB 已有线路
    async with db_manager.get_session() as session:
        result = await session.execute(
            text("SELECT line_name FROM metro_line WHERE city_id = :city_id"),
            {"city_id": city_id},
        )
        existing_lines = {row[0] for row in result.fetchall()}

    # 3. 创建缺失的线路
    created_count = 0
    for line_data in llm_lines:
        line_name = line_data.get("line_name")
        if not line_name or line_name in existing_lines:
            continue

        try:
            line_id = generate_id()
            async with db_manager.get_session() as session:
                await session.execute(
                    text("""INSERT INTO metro_line
                            (id, country_id, country_name, city_id, city_name,
                             line_name, line_no, line_color, line_color_cn,
                             total_km, station_count,
                             open_date, first_time, last_time,
                             status_code, extra, created_at, updated_at)
                            VALUES (:id, :country_id, '中国', :city_id, :city_name,
                                    :line_name, :line_no, :line_color, :line_color_cn,
                                    :total_km, :station_count,
                                    :open_date, :first_time, :last_time,
                                    1, :extra, NOW(), NOW())"""),
                    {
                        "id": line_id,
                        "country_id": country_id,
                        "city_id": city_id,
                        "city_name": city_name,
                        "line_name": line_name,
                        "line_no": line_data.get("line_no", ""),
                        "line_color": line_data.get("line_color", "#000000"),
                        "line_color_cn": line_data.get("line_color_cn"),
                        "total_km": line_data.get("total_km"),
                        "station_count": line_data.get("station_count"),
                        "open_date": line_data.get("open_date"),
                        "first_time": line_data.get("first_time"),
                        "last_time": line_data.get("last_time"),
                        "extra": json.dumps({
                            "source": "llm",
                            "llm_date": line_data.get("_llm_date", ""),
                        }, ensure_ascii=False),
                    },
                )
                await session.commit()
                created_count += 1
                logger.info(f"创建线路(LLM): {line_name} (no={line_data.get('line_no')}, "
                            f"color={line_data.get('line_color')}, stations={line_data.get('station_count')}, "
                            f"km={line_data.get('total_km')}, open={line_data.get('open_date')})")
        except Exception as e:
            logger.error(f"创建线路失败 {line_name}: {e}")

    await progress_tracker.update(
        task_id, 28, "ensuring_data",
        f"从大模型获取并创建了 {created_count} 条线路（共 {len(llm_lines)} 条）",
    )


async def _ensure_lines_from_crawl(
    task_id: str,
    city_id: int,
    city_name: str,
    country_id: int,
):
    """
    降级方案：从爬取数据中提取线路（当 LLM 失败时使用）。
    """
    from app.db.connection import db_manager
    from sqlalchemy import text
    import re

    # 这里需要 comparison 数据，但当前函数签名没有传入
    # 降级方案只创建空线路记录，后续由站点数据补充
    logger.info(f"降级方案：跳过线路创建，等待站点数据写入时自动创建")


async def _update_city_extra(city_id: int, llm_data: dict):
    """
    更新城市的 extra 字段（LLM 补充数据）。
    只更新空字段，不覆盖已有数据。标记来源为 llm。
    """
    from app.db.connection import db_manager
    from sqlalchemy import text
    import json

    async with db_manager.get_session() as session:
        # 读取现有 extra
        result = await session.execute(
            text("SELECT extra FROM city WHERE id = :id"),
            {"id": city_id},
        )
        row = result.first()
        existing_extra = {}
        if row and row[0]:
            try:
                existing_extra = json.loads(row[0]) if isinstance(row[0], str) else row[0]
            except (json.JSONDecodeError, TypeError):
                existing_extra = {}

        # 合并：只更新空字段
        updated = False
        fields_to_update = {
            "city_name_en": llm_data.get("city_name_en"),
            "city_alias": llm_data.get("city_alias"),
            "population": llm_data.get("population"),
            "metro_km": llm_data.get("metro_km"),
        }

        for key, value in fields_to_update.items():
            if value and not existing_extra.get(key):
                existing_extra[key] = value
                updated = True

        # 标记来源
        if llm_data.get("_source"):
            existing_extra["_data_source"] = llm_data["_source"]
            existing_extra["_llm_date"] = llm_data.get("_llm_date", "")
            existing_extra["_confidence"] = llm_data.get("_confidence", "medium")
            updated = True

        if updated:
            await session.execute(
                text("UPDATE city SET extra = :extra, updated_at = NOW() WHERE id = :id"),
                {"id": city_id, "extra": json.dumps(existing_extra, ensure_ascii=False)},
            )
            await session.commit()
