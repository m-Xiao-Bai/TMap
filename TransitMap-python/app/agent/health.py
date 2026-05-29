"""
Agent 服务 — 健康检查端点

提供服务健康状态、各组件连通性检查。
"""

import time
import logging
from fastapi import APIRouter

from app.db.connection import db_manager
from app.dependencies import redis_manager
from app.gateway.llm_gateway import llm_gateway
from app.clients.java_api import java_client

logger = logging.getLogger("tmap-python.health")

router = APIRouter(tags=["Health"])

# 服务启动时间
_start_time = time.time()


@router.get("/health")
async def health_check():
    """
    健康检查端点。

    返回各组件状态：
    - llm_gateway: LLM 网关是否可用
    - database: 数据库连接是否正常
    - redis: Redis 连接是否正常
    - java_api: Java 内部 API 是否可达

    整体状态：
    - "ok": 所有组件正常
    - "degraded": 部分组件异常
    """
    checks = {}

    # 数据库
    try:
        checks["database"] = await db_manager.ping()
    except Exception:
        checks["database"] = False

    # Redis
    try:
        checks["redis"] = await redis_manager.ping()
    except Exception:
        checks["redis"] = False

    # LLM 网关
    try:
        checks["llm_gateway"] = await llm_gateway.ping()
    except Exception:
        checks["llm_gateway"] = False

    # Java API
    try:
        checks["java_api"] = await java_client.ping()
    except Exception:
        checks["java_api"] = False

    all_ok = all(checks.values())
    return {
        "status": "ok" if all_ok else "degraded",
        "checks": checks,
        "uptime_seconds": int(time.time() - _start_time),
        "version": "1.0.0",
    }


@router.get("/health/llm")
async def llm_health():
    """LLM 供应商健康状态详情"""
    results = await llm_gateway.ping_all()
    return {
        "status": "ok" if any(results.values()) else "down",
        "providers": results,
    }
