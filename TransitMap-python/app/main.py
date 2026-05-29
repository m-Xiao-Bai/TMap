"""
TransitMap Python Service — FastAPI 入口

启动命令:
  conda activate tmap-python
  cd TransitMap-python
  uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
    uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
"""

import time
import logging
import traceback
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.config import settings
from app.db.connection import db_manager
from app.exceptions import TmapException

# ── 日志配置 ──
logging.basicConfig(
    level=logging.DEBUG if settings.DEBUG else logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("tmap-python")


# ── 生命周期管理 ──
@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时初始化资源，关闭时清理"""
    logger.info("=== TransitMap Python Service 启动中 ===")

    # 加载 LLM 供应商配置
    providers = settings.load_llm_providers()
    logger.info(f"已加载 {len(providers)} 个 LLM 供应商: {[p.name for p in providers]}")

    # 初始化数据库连接池
    await db_manager.init()
    logger.info("数据库连接池已初始化")

    # 初始化 Redis
    from app.dependencies import redis_manager
    await redis_manager.init()
    logger.info("Redis 连接已初始化")

    # 启动 LLM 网关
    from app.gateway.llm_gateway import llm_gateway
    await llm_gateway.init()
    logger.info("LLM 网关已初始化")

    # 启动爬虫 Worker
    from app.crawler.task_queue import crawler_queue
    from app.crawler.service import handle_crawl_task
    crawler_queue.set_handler(handle_crawl_task)
    await crawler_queue.start_workers()
    logger.info("爬虫任务队列 Worker 已启动")

    logger.info(f"=== 服务就绪 — 端口 {settings.SERVICE_PORT} ===")

    yield

    # 关闭清理
    logger.info("=== TransitMap Python Service 关闭中 ===")
    await crawler_queue.stop_workers()
    await redis_manager.close()
    await db_manager.close()
    logger.info("=== 资源已释放 ===")


# ── FastAPI 实例 ──
app = FastAPI(
    title="TransitMap Python Service",
    description="城市轨道交通 AI Agent + 数据爬取服务",
    version="1.0.0",
    lifespan=lifespan,
)

# ── CORS ──
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── 请求日志中间件 ──
@app.middleware("http")
async def request_logging_middleware(request: Request, call_next):
    start = time.time()
    response = await call_next(request)
    elapsed_ms = (time.time() - start) * 1000
    logger.info(
        f"{request.method} {request.url.path} → {response.status_code} ({elapsed_ms:.0f}ms)"
    )
    return response


# ── 全局异常处理 ──
@app.exception_handler(TmapException)
async def tmap_exception_handler(request: Request, exc: TmapException):
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "code": exc.code,
            "message": exc.message,
            "data": None,
        },
    )


@app.exception_handler(Exception)
async def general_exception_handler(request: Request, exc: Exception):
    # 获取详细的异常信息（文件名、行号、调用栈）
    tb = traceback.extract_tb(exc.__traceback__)
    if tb:
        last_frame = tb[-1]
        error_location = f"{last_frame.filename}:{last_frame.lineno} in {last_frame.name}"
        error_detail = f"{type(exc).__name__}: {exc}"
        logger.error(f"未处理异常 | 位置: {error_location} | 错误: {error_detail}")
        logger.error(f"完整调用栈:\n{traceback.format_exc()}")
    else:
        logger.exception(f"未处理异常: {exc}")

    return JSONResponse(
        status_code=500,
        content={
            "code": "INTERNAL_ERROR",
            "message": f"{type(exc).__name__}: {str(exc)}",
            "data": None,
        },
    )


# ── 注册路由 ──
from app.agent.router import router as agent_router
from app.agent.health import router as health_router
from app.crawler.router import router as crawler_router
from app.crawler.review.router import router as review_router
from app.quality.router import router as quality_router

app.include_router(health_router)
app.include_router(agent_router, prefix="/api/agent", tags=["Agent"])
app.include_router(crawler_router, prefix="/api/crawler", tags=["Crawler"])
app.include_router(review_router, prefix="/api/review", tags=["Review"])
app.include_router(quality_router, prefix="/api/quality", tags=["Quality"])


# ── 根路径 ──
@app.get("/")
async def root():
    return {
        "service": "TransitMap Python Service",
        "version": "1.0.0",
        "status": "running",
    }
