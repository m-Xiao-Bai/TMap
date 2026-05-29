"""
爬虫服务 — Redis 任务队列

使用 Redis List 实现任务队列，支持：
- 任务入队/出队
- 任务状态管理
- 进度追踪
- 取消任务
"""

import json
import uuid
import asyncio
import logging
from typing import Callable, Awaitable

from app.dependencies import redis_manager

logger = logging.getLogger("tmap-python.crawler.queue")

# Redis Key 前缀
QUEUE_KEY = "tmap:crawler:queue"
TASK_KEY = "tmap:crawler:task"
PROGRESS_KEY = "tmap:crawler:progress"


class CrawlerTaskQueue:
    """爬虫任务队列管理器"""

    def __init__(self):
        self._worker_task: asyncio.Task | None = None
        self._running = False
        self._handler: Callable[[dict], Awaitable[None]] | None = None

    def set_handler(self, handler: Callable[[dict], Awaitable[None]]):
        """设置任务处理函数"""
        self._handler = handler

    async def enqueue(self, city_name: str, country_id: int, sources: str, trigger_user_id: int) -> str:
        """
        入队一个爬取任务。

        Returns:
            task_id (UUID)
        """
        r = redis_manager.client
        task_id = str(uuid.uuid4())

        task_data = {
            "task_id": task_id,
            "city_name": city_name,
            "country_id": country_id,
            "sources": sources,
            "trigger_user_id": trigger_user_id,
            "status": "pending",
        }

        # 保存任务详情
        await r.hset(f"{TASK_KEY}:{task_id}", mapping={
            k: json.dumps(v) if isinstance(v, (dict, list)) else str(v)
            for k, v in task_data.items()
        })

        # 入队
        await r.rpush(QUEUE_KEY, task_id)

        logger.info(f"任务已入队: {task_id} — {city_name}")
        return task_id

    async def enqueue_batch(self, cities: list[dict], trigger_user_id: int) -> list[str]:
        """批量入队"""
        task_ids = []
        for city in cities:
            task_id = await self.enqueue(
                city_name=city["city_name"],
                country_id=city.get("country_id", 1),
                sources=city.get("sources", "wikipedia,baike"),
                trigger_user_id=trigger_user_id,
            )
            task_ids.append(task_id)
        return task_ids

    async def get_task(self, task_id: str) -> dict | None:
        """获取任务详情"""
        r = redis_manager.client
        data = await r.hgetall(f"{TASK_KEY}:{task_id}")
        if not data:
            return None
        return data

    async def update_task_status(self, task_id: str, status: str, **extra):
        """更新任务状态"""
        r = redis_manager.client
        mapping = {"status": status}
        mapping.update(extra)
        await r.hset(f"{TASK_KEY}:{task_id}", mapping=mapping)

    async def get_pending_count(self) -> int:
        """获取队列中待处理任务数"""
        r = redis_manager.client
        return await r.llen(QUEUE_KEY)

    async def get_all_tasks(self) -> list[dict]:
        """获取所有任务"""
        r = redis_manager.client
        # 扫描所有 task key
        tasks = []
        cursor = 0
        while True:
            cursor, keys = await r.scan(cursor, match=f"{TASK_KEY}:*", count=100)
            for key in keys:
                data = await r.hgetall(key)
                if data:
                    tasks.append(data)
            if cursor == 0:
                break
        return sorted(tasks, key=lambda t: t.get("created_at", ""), reverse=True)

    async def cancel_task(self, task_id: str) -> bool:
        """取消任务"""
        r = redis_manager.client
        task = await self.get_task(task_id)
        if not task:
            return False

        status = task.get("status", "")
        if status in ("completed", "failed", "cancelled"):
            return False

        await self.update_task_status(task_id, "cancelled")
        # 从队列中移除（如果还在）
        await r.lrem(QUEUE_KEY, 0, task_id)
        return True

    # ── Worker ──

    async def start_workers(self):
        """启动 Worker 协程"""
        if self._running:
            return
        self._running = True
        self._worker_task = asyncio.create_task(self._worker_loop())
        logger.info("爬虫 Worker 已启动")

    async def stop_workers(self):
        """停止 Worker"""
        self._running = False
        if self._worker_task:
            self._worker_task.cancel()
            try:
                await self._worker_task
            except asyncio.CancelledError:
                pass
        logger.info("爬虫 Worker 已停止")

    async def _worker_loop(self):
        """Worker 主循环：从队列取任务并执行"""
        r = redis_manager.client
        while self._running:
            try:
                # 阻塞等待任务，超时 2 秒
                result = await r.brpop(QUEUE_KEY, timeout=2)
                if not result:
                    continue

                _, task_id = result
                task_id = task_id if isinstance(task_id, str) else task_id.decode()

                # 检查任务是否已取消
                task = await self.get_task(task_id)
                if not task or task.get("status") == "cancelled":
                    continue

                logger.info(f"开始处理任务: {task_id}")

                # 更新状态为运行中
                await self.update_task_status(task_id, "running")

                # 执行处理函数
                if self._handler:
                    try:
                        await self._handler(task)
                        # handler 内部负责更新最终状态
                    except Exception as e:
                        logger.error(f"任务 {task_id} 处理失败: {e}")
                        await self.update_task_status(
                            task_id, "failed",
                            error_message=str(e),
                        )
                else:
                    logger.warning("未设置任务处理函数")
                    await self.update_task_status(task_id, "failed", error_message="未设置处理函数")

            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Worker 循环异常: {e}")
                await asyncio.sleep(1)


# 全局单例
crawler_queue = CrawlerTaskQueue()
