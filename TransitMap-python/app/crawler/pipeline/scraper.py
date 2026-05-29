"""
爬虫管线 — Step 1: 数据爬取

并行调用多个数据源，汇总结果。
"""

import asyncio
import logging
from app.crawler.sources.base import CrawlResult
from app.crawler.sources.wikipedia import WikipediaSource
from app.crawler.sources.baike import BaikeSource
from app.crawler.sources.osm import OsmSource
from app.crawler.progress_tracker import progress_tracker

logger = logging.getLogger("tmap-python.crawler.pipeline.scraper")

# 数据源注册表
SOURCES = {
    "wikipedia": WikipediaSource,
    "baike": BaikeSource,
    "osm": OsmSource,
}


async def scrape(task_id: str, city_name: str, sources: list[str]) -> list[CrawlResult]:
    """
    并行爬取多个数据源。

    Args:
        task_id: 任务 ID（用于进度追踪）
        city_name: 城市名称
        sources: 数据源列表，如 ["wikipedia", "baike", "osm"]

    Returns:
        各数据源的爬取结果列表
    """
    await progress_tracker.update(
        task_id, 5, "scraping",
        f"开始从 {len(sources)} 个数据源爬取: {', '.join(sources)}",
    )

    # 创建数据源实例
    source_instances = []
    for src_name in sources:
        cls = SOURCES.get(src_name)
        if cls:
            source_instances.append(cls())
        else:
            logger.warning(f"未知数据源: {src_name}")

    if not source_instances:
        return []

    # 并行爬取
    async def crawl_one(source):
        try:
            logger.info(f"[{source.name}] 开始爬取 {city_name}")
            result = await source.crawl(city_name)
            logger.info(f"[{source.name}] 完成: {len(result.lines)} 线路, {len(result.stations)} 站点")
            return result
        except Exception as e:
            logger.error(f"[{source.name}] 爬取异常: {e}")
            return CrawlResult(
                source=source.name,
                city_name=city_name,
                success=False,
                error=str(e),
            )

    tasks = [crawl_one(src) for src in source_instances]
    results = await asyncio.gather(*tasks)

    # 汇总统计
    total_lines = sum(len(r.lines) for r in results if r.success)
    total_stations = sum(len(r.stations) for r in results if r.success)
    success_sources = [r.source for r in results if r.success]

    await progress_tracker.update(
        task_id, 15, "scraping",
        f"爬取完成: {len(success_sources)}/{len(sources)} 个源成功, "
        f"共 {total_lines} 条线路, {total_stations} 个站点",
        detail={
            "success_sources": success_sources,
            "total_lines": total_lines,
            "total_stations": total_stations,
        },
    )

    return list(results)
