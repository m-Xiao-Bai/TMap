"""
爬虫服务 — API 路由
"""

import logging
from fastapi import APIRouter, Depends

from app.dependencies import verify_api_key
from app.crawler.schemas import (
    CrawlerTriggerRequest,
    CrawlerBatchRequest,
    CrawlerTaskResponse,
)
from app.crawler.task_queue import crawler_queue

logger = logging.getLogger("tmap-python.crawler")

router = APIRouter()


@router.post("/trigger")
async def trigger_crawl(
    request: CrawlerTriggerRequest,
    api_key: str = Depends(verify_api_key),
):
    """
    触发单城市爬取。

    任务入队后立即返回 task_id，实际爬取在后台异步执行。
    通过 WebSocket 或轮询 GET /task/{task_id} 获取进度。
    """
    task_id = await crawler_queue.enqueue(
        city_name=request.city_name,
        country_id=request.country_id,
        sources="osm",  # 固定使用 OSM
        trigger_user_id=0,  # 由 Java 端传入
    )
    return {
        "code": 200,
        "message": f"爬取任务已入队: {request.city_name}",
        "data": {
            "task_id": task_id,
            "city_name": request.city_name,
            "status": "pending",
        },
    }


@router.post("/batch")
async def trigger_batch_crawl(
    request: CrawlerBatchRequest,
    api_key: str = Depends(verify_api_key),
):
    """
    批量爬取多个城市。

    所有任务依次入队，按顺序执行。
    """
    task_ids = await crawler_queue.enqueue_batch(
        cities=[city.model_dump() for city in request.cities],
        trigger_user_id=0,
    )
    return {
        "code": 200,
        "message": f"已入队 {len(task_ids)} 个爬取任务",
        "data": {
            "task_ids": task_ids,
            "count": len(task_ids),
        },
    }


@router.get("/task/{task_id}")
async def get_task_status(
    task_id: str,
    api_key: str = Depends(verify_api_key),
):
    """查询任务状态"""
    task = await crawler_queue.get_task(task_id)
    if not task:
        return {"code": 404, "message": "任务不存在", "data": None}
    return {"code": 200, "data": task}


@router.delete("/task/{task_id}")
async def cancel_task(
    task_id: str,
    api_key: str = Depends(verify_api_key),
):
    """取消任务"""
    success = await crawler_queue.cancel_task(task_id)
    if not success:
        return {"code": 400, "message": "任务无法取消（已完成或不存在）"}
    return {"code": 200, "message": "任务已取消"}


@router.get("/tasks")
async def list_tasks(
    api_key: str = Depends(verify_api_key),
):
    """获取所有任务列表"""
    tasks = await crawler_queue.get_all_tasks()
    return {"code": 200, "data": tasks}
