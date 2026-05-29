"""
爬虫服务 — 进度追踪器

通过 Redis pub/sub 推送爬取进度，支持 WebSocket 订阅。
"""

import json
import logging
from typing import Optional

from app.dependencies import redis_manager

logger = logging.getLogger("tmap-python.crawler.progress")

PROGRESS_CHANNEL = "tmap:crawler:progress"


class ProgressTracker:
    """爬取进度追踪器"""

    async def update(
        self,
        task_id: str,
        progress_pct: int,
        step: str,
        message: str,
        detail: Optional[dict] = None,
    ):
        """
        更新任务进度并发布到 Redis pub/sub。

        Args:
            task_id: 任务 ID
            progress_pct: 进度百分比 0-100
            step: 当前步骤名称
            message: 进度描述
            detail: 额外详情
        """
        r = redis_manager.client

        # 更新任务详情中的进度
        await r.hset(f"tmap:crawler:task:{task_id}", mapping={
            "progress_pct": str(progress_pct),
            "current_step": step,
        })

        # 发布进度消息
        payload = {
            "type": "crawler_progress",
            "task_id": task_id,
            "progress": progress_pct,
            "step": step,
            "message": message,
        }
        if detail:
            payload["detail"] = detail

        await r.publish(PROGRESS_CHANNEL, json.dumps(payload, ensure_ascii=False))
        logger.debug(f"[{task_id}] 进度 {progress_pct}% — {message}")

    async def complete(self, task_id: str, report: dict):
        """任务完成"""
        r = redis_manager.client
        payload = {
            "type": "crawler_complete",
            "task_id": task_id,
            "report": report,
        }
        await r.publish(PROGRESS_CHANNEL, json.dumps(payload, ensure_ascii=False))

    async def error(self, task_id: str, error_message: str):
        """任务失败"""
        r = redis_manager.client
        payload = {
            "type": "crawler_error",
            "task_id": task_id,
            "error": error_message,
        }
        await r.publish(PROGRESS_CHANNEL, json.dumps(payload, ensure_ascii=False))


# 全局单例
progress_tracker = ProgressTracker()
