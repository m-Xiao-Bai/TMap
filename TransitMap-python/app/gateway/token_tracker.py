"""
LLM 网关 — Token 用量统计

使用 Redis 记录每个用户/会话的 Token 用量。
"""

import time
import logging
from dataclasses import dataclass

from app.dependencies import redis_manager
from app.gateway.schemas import LlmUsage

logger = logging.getLogger("tmap-python.llm")


@dataclass
class UsageStats:
    """用量统计"""
    total_input_tokens: int = 0
    total_output_tokens: int = 0
    total_calls: int = 0
    period_start: float = 0
    period_end: float = 0


class TokenTracker:
    """Token 用量追踪器"""

    PREFIX = "tmap:token"

    async def track(
        self,
        user_id: int | None,
        session_id: str,
        provider: str,
        model: str,
        usage: LlmUsage,
    ):
        """记录一次 LLM 调用的 Token 用量"""
        r = redis_manager.client
        now = int(time.time())
        date_key = time.strftime("%Y%m%d", time.localtime(now))

        pipe = r.pipeline()

        # 全局总量
        pipe.hincrby(f"{self.PREFIX}:total", "input", usage.input_tokens)
        pipe.hincrby(f"{self.PREFIX}:total", "output", usage.output_tokens)
        pipe.hincrby(f"{self.PREFIX}:total", "calls", 1)

        # 按日期
        pipe.hincrby(f"{self.PREFIX}:daily:{date_key}", "input", usage.input_tokens)
        pipe.hincrby(f"{self.PREFIX}:daily:{date_key}", "output", usage.output_tokens)
        pipe.hincrby(f"{self.PREFIX}:daily:{date_key}", "calls", 1)

        # 按供应商
        pipe.hincrby(f"{self.PREFIX}:provider:{provider}", "input", usage.input_tokens)
        pipe.hincrby(f"{self.PREFIX}:provider:{provider}", "output", usage.output_tokens)
        pipe.hincrby(f"{self.PREFIX}:provider:{provider}", "calls", 1)

        # 按用户
        if user_id:
            pipe.hincrby(f"{self.PREFIX}:user:{user_id}", "input", usage.input_tokens)
            pipe.hincrby(f"{self.PREFIX}:user:{user_id}", "output", usage.output_tokens)
            pipe.hincrby(f"{self.PREFIX}:user:{user_id}", "calls", 1)

        await pipe.execute()

    async def get_total(self) -> UsageStats:
        """获取全局总量"""
        r = redis_manager.client
        data = await r.hgetall(f"{self.PREFIX}:total")
        return UsageStats(
            total_input_tokens=int(data.get("input", 0)),
            total_output_tokens=int(data.get("output", 0)),
            total_calls=int(data.get("calls", 0)),
        )

    async def get_daily(self, date_str: str = "") -> UsageStats:
        """获取某日用量"""
        if not date_str:
            date_str = time.strftime("%Y%m%d")
        r = redis_manager.client
        data = await r.hgetall(f"{self.PREFIX}:daily:{date_str}")
        return UsageStats(
            total_input_tokens=int(data.get("input", 0)),
            total_output_tokens=int(data.get("output", 0)),
            total_calls=int(data.get("calls", 0)),
        )

    async def get_by_provider(self, provider: str) -> UsageStats:
        """获取某供应商用量"""
        r = redis_manager.client
        data = await r.hgetall(f"{self.PREFIX}:provider:{provider}")
        return UsageStats(
            total_input_tokens=int(data.get("input", 0)),
            total_output_tokens=int(data.get("output", 0)),
            total_calls=int(data.get("calls", 0)),
        )

    async def get_by_user(self, user_id: int) -> UsageStats:
        """获取某用户用量"""
        r = redis_manager.client
        data = await r.hgetall(f"{self.PREFIX}:user:{user_id}")
        return UsageStats(
            total_input_tokens=int(data.get("input", 0)),
            total_output_tokens=int(data.get("output", 0)),
            total_calls=int(data.get("calls", 0)),
        )


# 全局单例
token_tracker = TokenTracker()
