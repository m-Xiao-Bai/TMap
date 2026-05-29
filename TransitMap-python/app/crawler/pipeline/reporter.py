"""
爬虫管线 — Step 6: 生成报告
"""

import logging
from app.crawler.progress_tracker import progress_tracker
from app.crawler.task_queue import crawler_queue

logger = logging.getLogger("tmap-python.crawler.pipeline.reporter")


async def generate_report(
    task_id: str,
    city_name: str,
    source_stats: dict,
    confidence_summary: dict,
    db_stats: dict,
    quality_score: float,
    errors: list[str],
) -> dict:
    """
    生成爬取报告并更新任务状态。

    Returns:
        完整的爬取报告
    """
    report = {
        "task_id": task_id,
        "city_name": city_name,
        "source_stats": source_stats,
        "confidence_summary": confidence_summary,
        "db_stats": db_stats,
        "quality_score": quality_score,
        "errors": errors,
    }

    # 更新任务最终状态
    updates = {
        "status": "completed" if not errors else "completed",
        "progress_pct": "100",
        "current_step": "completed",
        "lines_found": str(sum(s.get("lines", 0) for s in source_stats.values())),
        "stations_found": str(sum(s.get("stations", 0) for s in source_stats.values())),
        "lines_inserted": str(db_stats.get("lines_inserted", 0)),
        "lines_updated": str(db_stats.get("lines_updated", 0)),
        "stations_inserted": str(db_stats.get("stations_inserted", 0)),
        "stations_updated": str(db_stats.get("stations_updated", 0)),
        "stations_skipped": str(db_stats.get("stations_skipped", 0)),
        "stations_pending_review": str(db_stats.get("stations_pending_review", 0)),
    }
    if errors:
        updates["error_message"] = "; ".join(errors[:5])

    await crawler_queue.update_task_status(task_id, **updates)

    # 推送完成通知
    await progress_tracker.complete(task_id, report)

    logger.info(
        f"爬取报告: {city_name} — "
        f"线路 {updates['lines_inserted']} 新增, "
        f"站点 {updates['stations_inserted']} 新增, "
        f"{updates['stations_skipped']} 跳过, "
        f"{updates['stations_pending_review']} 待审核, "
        f"质量评分 {quality_score:.2f}"
    )

    return report
