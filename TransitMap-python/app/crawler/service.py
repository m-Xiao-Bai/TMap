"""
爬虫服务 — 编排器

串联 6 步管线，处理整个爬取流程。
"""

import logging
from app.crawler.pipeline.scraper import scrape
from app.crawler.pipeline.llm_validator import validate_with_llm
from app.crawler.pipeline.geo_coder import geocode_stations
from app.crawler.pipeline.quality_checker import check_quality
from app.crawler.pipeline.db_writer import write_to_db
from app.crawler.pipeline.reporter import generate_report
from app.crawler.sources.comparator import SourceComparator
from app.crawler.progress_tracker import progress_tracker
from app.crawler.task_queue import crawler_queue

logger = logging.getLogger("tmap-python.crawler.service")

comparator = SourceComparator()


async def handle_crawl_task(task_data: dict):
    """
    处理一个爬取任务（Worker 调用此函数）。

    完整管线：
    ① 并行爬取 → ② 多源对比 → ③ LLM 校验 → ④ 坐标补全 → ⑤ 质检 → ⑥ 写入 DB → ⑦ 报告
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

        # ③ LLM 校验 + 地址补全
        comparison.merged_stations = await validate_with_llm(
            task_id, city_name,
            comparison.merged_stations,
            comparison.merged_lines,
        )

        # ④ 高德坐标补全
        comparison.merged_stations = await geocode_stations(
            task_id, city_name,
            comparison.merged_stations,
        )

        # ⑤ 数据质检
        quality_report = await check_quality(
            task_id, city_name,
            comparison.merged_stations,
            comparison.merged_lines,
        )

        # ⑥ 增量写入 DB
        # 需要先查找或创建城市记录
        city_id = await _ensure_city(city_name, country_id)

        db_stats = await write_to_db(
            task_id, country_id, "中国", city_id, city_name,
            comparison, quality_report,
        )

        # ⑦ 生成报告
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


async def _ensure_city(city_name: str, country_id: int) -> int:
    """确保城市记录存在，返回 city_id"""
    from app.db.connection import db_manager
    from sqlalchemy import text

    async with db_manager.get_session() as session:
        # 查找已有城市
        result = await session.execute(
            text("SELECT id FROM city WHERE city_name LIKE :name LIMIT 1"),
            {"name": f"%{city_name.replace('市', '')}%"},
        )
        row = result.first()
        if row:
            return row[0]

        # 创建新城市
        result = await session.execute(
            text("""INSERT INTO city
                    (country_id, country_name, city_name, city_name_en, city_alias, status_code, created_at, updated_at)
                    VALUES (:country_id, '中国', :city_name, '', '', 1, NOW(), NOW())"""),
            {"country_id": country_id, "city_name": city_name},
        )
        await session.commit()
        return result.lastrowid
