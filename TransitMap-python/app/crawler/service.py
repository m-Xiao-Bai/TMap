"""
爬虫服务 — 编排器

串联 6 步管线，处理整个爬取流程。
优化：城市/线路数据从爬取结果推导，LLM 只用于补充无法获取的信息。
"""

import logging
from app.crawler.pipeline.scraper import scrape
from app.crawler.pipeline.llm_validator import validate_with_llm
from app.crawler.pipeline.geo_coder import geocode_stations
from app.crawler.pipeline.quality_checker import check_quality
from app.crawler.pipeline.db_writer import write_to_db, update_city_stats
from app.crawler.pipeline.reporter import generate_report
from app.crawler.pipeline.city_enricher import enrich_city_with_llm
from app.crawler.sources.comparator import SourceComparator
from app.crawler.progress_tracker import progress_tracker
from app.crawler.task_queue import crawler_queue

logger = logging.getLogger("tmap-python.crawler.service")

comparator = SourceComparator()


async def handle_crawl_task(task_data: dict):
    """
    处理一个爬取任务（Worker 调用此函数）。

    完整管线：
    ① 并行爬取 → ② 多源对比 → ③ 确保城市/线路存在 → ④ LLM 校验 → ⑤ 坐标补全 → ⑥ 质检 → ⑦ 写入 DB → ⑧ 报告
    """
    task_id = task_data.get("task_id", "")
    city_name = task_data.get("city_name", "")
    country_id = int(task_data.get("country_id", 1))
    sources_str = task_data.get("sources", "wikipedia,baike,osm")
    sources = [s.strip() for s in sources_str.split(",") if s.strip()]

    errors = []

    try:
        logger.info(f"[{task_id}] 开始处理: {city_name} (sources={sources})")

        # ① 并行爬取
        crawl_results = await scrape(task_id, city_name, sources)
        success_count = sum(1 for r in crawl_results if r.success)
        if success_count == 0:
            errors.append("所有数据源爬取失败")
            await crawler_queue.update_task_status(task_id, "failed", error_message="所有数据源爬取失败")
            await progress_tracker.error(task_id, "所有数据源爬取失败")
            return

        # ② 多源对比
        await progress_tracker.update(task_id, 20, "comparing", "正在进行多源数据对比...")
        comparison = comparator.compare(crawl_results)

        if not comparison.merged_stations:
            errors.append("对比后无有效站点数据")
            await crawler_queue.update_task_status(task_id, "failed", error_message="无有效站点数据")
            await progress_tracker.error(task_id, "无有效站点数据")
            return

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
        logger.error(f"[{task_id}] 任务异常: {e}", exc_info=True)
        errors.append(str(e))
        try:
            await crawler_queue.update_task_status(task_id, "failed", error_message=str(e))
            await progress_tracker.error(task_id, str(e))
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
            # 2. 创建最小城市记录（必填字段）
            result = await session.execute(
                text("""INSERT INTO city
                        (country_id, country_name, city_name, city_name_en, city_alias,
                         metro_line_count, metro_count, status_code, created_at, updated_at)
                        VALUES (:country_id, '中国', :city_name, '', '', 0, 0, 1, NOW(), NOW())"""),
                {"country_id": country_id, "city_name": city_name},
            )
            await session.commit()
            city_id = result.lastrowid
            logger.info(f"创建城市最小记录: {city_name} (ID={city_id})")

            # 3. 异步 LLM 补充城市信息（不阻塞主流程）
            try:
                llm_data = await enrich_city_with_llm(city_name)
                if llm_data and llm_data.get("_source") != "llm_failed":
                    await _update_city_extra(city_id, llm_data)
                    logger.info(f"LLM 补充城市信息完成: {city_name}")
            except Exception as e:
                logger.warning(f"LLM 补充城市信息失败（不影响主流程）: {e}")

        # 4. 从爬取数据推导线路并确保存在
        await _ensure_lines(task_id, city_id, city_name, country_id, comparison)

        return city_id


async def _ensure_lines(
    task_id: str,
    city_id: int,
    city_name: str,
    country_id: int,
    comparison,
):
    """
    从爬取站点数据推导线路，确保线路记录存在。

    优化：不问 LLM，直接从站点的 line_names 提取。
    """
    from app.db.connection import db_manager
    from sqlalchemy import text
    import re

    # 从站点数据提取所有线路名（去重）
    line_names_from_stations = set()
    for station in comparison.merged_stations:
        for ln in station.line_names:
            if ln:
                line_names_from_stations.add(ln)

    # 也从 comparison.merged_lines 获取颜色信息
    line_color_map = {}
    for line in comparison.merged_lines:
        if line.color:
            line_color_map[line.name] = line.color

    if not line_names_from_stations:
        logger.warning("爬取数据中没有线路信息")
        return

    # 检查 DB 已有线路
    async with db_manager.get_session() as session:
        result = await session.execute(
            text("SELECT line_name FROM metro_line WHERE city_id = :city_id"),
            {"city_id": city_id},
        )
        existing_lines = {row[0] for row in result.fetchall()}

    # 创建缺失的线路
    created_count = 0
    for line_name in sorted(line_names_from_stations):
        if line_name in existing_lines:
            continue

        # 从线路名提取编号
        line_no = re.sub(r"[^0-9a-zA-Z]", "", line_name) or line_name
        line_color = line_color_map.get(line_name, "#000000")

        try:
            async with db_manager.get_session() as session:
                await session.execute(
                    text("""INSERT INTO metro_line
                            (country_id, country_name, city_id, city_name,
                             line_name, line_no, line_color, status_code, created_at, updated_at)
                            VALUES (:country_id, '中国', :city_id, :city_name,
                                    :line_name, :line_no, :line_color, 1, NOW(), NOW())"""),
                    {
                        "country_id": country_id,
                        "city_id": city_id,
                        "city_name": city_name,
                        "line_name": line_name,
                        "line_no": line_no,
                        "line_color": line_color,
                    },
                )
                await session.commit()
                created_count += 1
                logger.info(f"创建线路: {line_name} (line_no={line_no}, color={line_color})")
        except Exception as e:
            logger.error(f"创建线路失败 {line_name}: {e}")

    if created_count > 0:
        await progress_tracker.update(
            task_id, 28, "ensuring_data",
            f"创建了 {created_count} 条线路记录",
        )


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
