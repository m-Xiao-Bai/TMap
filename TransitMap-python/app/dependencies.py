"""
TransitMap Python Service — 依赖注入

FastAPI Depends() 使用的公共依赖。
"""

import logging
import redis.asyncio as aioredis
from fastapi import Depends, Header, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.db.connection import db_manager
from app.exceptions import AuthenticationError

logger = logging.getLogger("tmap-python.deps")


# ══════════════════════════════════════
# Redis 管理器
# ══════════════════════════════════════

class RedisManager:
    """异步 Redis 连接管理器"""

    def __init__(self):
        self._client: aioredis.Redis | None = None

    async def init(self):
        self._client = aioredis.from_url(
            settings.REDIS_URL,
            decode_responses=True,
            max_connections=20,
        )
        # 验证连接
        try:
            await self._client.ping()
            logger.info("Redis 连接验证成功")
        except Exception as e:
            logger.error(f"Redis 连接失败: {e}")
            raise

    async def close(self):
        if self._client:
            await self._client.close()
            logger.info("Redis 连接已关闭")

    @property
    def client(self) -> aioredis.Redis:
        if not self._client:
            raise RuntimeError("Redis 未初始化")
        return self._client

    async def ping(self) -> bool:
        try:
            return await self._client.ping()
        except Exception:
            return False


# 全局单例
redis_manager = RedisManager()


# ══════════════════════════════════════
# FastAPI 依赖注入函数
# ══════════════════════════════════════

async def get_db_session() -> AsyncSession:
    """获取数据库 session（自动关闭）"""
    session = db_manager.get_session()
    try:
        yield session
    finally:
        await session.close()


def get_redis() -> aioredis.Redis:
    """获取 Redis 客户端"""
    return redis_manager.client


async def verify_api_key(
    request: Request,
    x_api_key: str = Header(default="", alias="X-API-Key"),
) -> str:
    """
    验证 API Key 认证。

    Python 服务接受来自 Java 后端的调用，使用 X-API-Key header 认证。
    如果未配置 API Key，则跳过验证（开发模式）。
    """
    if not settings.SERVICE_API_KEY:
        # 未配置 API Key，跳过验证（开发模式）
        return "dev"

    if not x_api_key:
        raise AuthenticationError("缺少 X-API-Key 请求头")

    if x_api_key != settings.SERVICE_API_KEY:
        raise AuthenticationError("API Key 无效")

    return x_api_key


# ══════════════════════════════════════
# LLM 网关依赖
# ══════════════════════════════════════

def get_llm_gateway():
    """获取 LLM 网关实例"""
    from app.gateway.llm_gateway import llm_gateway
    return llm_gateway
