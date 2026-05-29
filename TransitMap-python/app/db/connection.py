"""
TransitMap Python Service — 数据库连接管理

使用 SQLAlchemy 2.0 async 模式，与 Java 共享同一个 MySQL 数据库。
"""

import logging
from sqlalchemy.ext.asyncio import (
    create_async_engine,
    async_sessionmaker,
    AsyncSession,
    AsyncEngine,
)
from sqlalchemy import text
from app.config import settings

logger = logging.getLogger("tmap-python.db")


class DatabaseManager:
    """异步数据库连接管理器"""

    def __init__(self):
        self._engine: AsyncEngine | None = None
        self._session_factory: async_sessionmaker[AsyncSession] | None = None

    async def init(self):
        """初始化连接池"""
        # 将 mysql:// 转换为 mysql+aiomysql://（如果需要）或使用 pymysql + asyncio
        url = settings.DATABASE_URL
        # pymysql 不支持 async，使用 aiomysql 作为后端
        if url.startswith("mysql+pymysql://"):
            url = url.replace("mysql+pymysql://", "mysql+aiomysql://", 1)

        self._engine = create_async_engine(
            url,
            pool_size=settings.DB_POOL_SIZE,
            max_overflow=settings.DB_MAX_OVERFLOW,
            pool_recycle=settings.DB_POOL_RECYCLE,
            echo=settings.DEBUG,
        )

        self._session_factory = async_sessionmaker(
            bind=self._engine,
            class_=AsyncSession,
            expire_on_commit=False,
        )

        # 验证连接
        try:
            async with self._engine.begin() as conn:
                await conn.execute(text("SELECT 1"))
            logger.info("数据库连接验证成功")
        except Exception as e:
            logger.error(f"数据库连接失败: {e}")
            raise

    async def close(self):
        """关闭连接池"""
        if self._engine:
            await self._engine.dispose()
            logger.info("数据库连接池已关闭")

    def get_session(self) -> AsyncSession:
        """
        获取一个新的数据库 session。

        用法:
            async with db_manager.get_session() as session:
                result = await session.execute(text("SELECT 1"))
        """
        if not self._session_factory:
            raise RuntimeError("数据库未初始化，请先调用 init()")
        return self._session_factory()

    async def ping(self) -> bool:
        """健康检查"""
        try:
            async with self._engine.begin() as conn:
                await conn.execute(text("SELECT 1"))
            return True
        except Exception:
            return False


# 全局单例
db_manager = DatabaseManager()
