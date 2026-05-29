"""
TransitMap Python Service — 数据访问层

与 Java 共享同一个 MySQL 数据库，直接读写表。
使用 SQLAlchemy Core（非 ORM）以保持轻量。
"""

import logging
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
from app.db.connection import db_manager

logger = logging.getLogger("tmap-python.db.repo")


class MetroRepository:
    """轨道交通数据仓库"""

    async def get_city_by_name(self, name: str) -> dict | None:
        """按名称查找城市"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("SELECT * FROM city WHERE city_name LIKE :name OR city_alias LIKE :name LIMIT 1"),
                {"name": f"%{name}%"},
            )
            row = result.mappings().first()
            return dict(row) if row else None

    async def get_stations_by_city(self, city_id: int) -> list[dict]:
        """获取城市所有站点"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("SELECT * FROM metro_station WHERE city_id = :city_id ORDER BY station_name"),
                {"city_id": city_id},
            )
            return [dict(row) for row in result.mappings().all()]

    async def get_lines_by_city(self, city_id: int) -> list[dict]:
        """获取城市所有线路"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("SELECT * FROM metro_line WHERE city_id = :city_id ORDER BY line_name"),
                {"city_id": city_id},
            )
            return [dict(row) for row in result.mappings().all()]

    async def find_station_by_name(self, name: str, city_id: int) -> dict | None:
        """按名称查找站点"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("""SELECT * FROM metro_station
                        WHERE city_id = :city_id
                        AND (station_name = :name OR station_name LIKE :like_name)
                        LIMIT 1"""),
                {"city_id": city_id, "name": name, "like_name": f"%{name}%"},
            )
            row = result.mappings().first()
            return dict(row) if row else None

    async def insert_line(self, line: dict) -> int:
        """插入线路，返回 ID"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("""INSERT INTO metro_line
                        (country_id, country_name, city_id, city_name, line_name, line_name_en, line_color, status, create_time, update_time)
                        VALUES (:country_id, :country_name, :city_id, :city_name, :line_name, :line_name_en, :line_color, 1, NOW(), NOW())"""),
                line,
            )
            await session.commit()
            return result.lastrowid

    async def insert_station(self, station: dict) -> int:
        """插入站点，返回 ID"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("""INSERT INTO metro_station
                        (country_id, country_name, city_id, city_name, line_ids, line_names, station_name, station_name_en, station_alias, osmid, longitude, latitude, status, create_time, update_time)
                        VALUES (:country_id, :country_name, :city_id, :city_name, :line_ids, :line_names, :station_name, :station_name_en, :station_alias, :osmid, :longitude, :latitude, 1, NOW(), NOW())"""),
                station,
            )
            await session.commit()
            return result.lastrowid

    async def update_station_location(self, station_id: int, lat: float, lng: float, address: str = ""):
        """更新站点坐标"""
        async with db_manager.get_session() as session:
            await session.execute(
                text("""UPDATE metro_station
                        SET latitude = :lat, longitude = :lng, update_time = NOW()
                        WHERE id = :id"""),
                {"id": station_id, "lat": lat, "lng": lng},
            )
            await session.commit()


class CrawlerRepository:
    """爬虫任务数据仓库"""

    async def create_task(self, task: dict) -> int:
        """创建爬取任务记录"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("""INSERT INTO crawler_task
                        (task_id, city_name, country_id, status, sources, trigger_user_id, created_at)
                        VALUES (:task_id, :city_name, :country_id, 'pending', :sources, :trigger_user_id, NOW())"""),
                task,
            )
            await session.commit()
            return result.lastrowid

    async def update_task(self, task_id: str, updates: dict):
        """更新任务状态"""
        if not updates:
            return
        set_clauses = ", ".join(f"{k} = :{k}" for k in updates.keys())
        async with db_manager.get_session() as session:
            await session.execute(
                text(f"UPDATE crawler_task SET {set_clauses}, updated_at = NOW() WHERE task_id = :task_id"),
                {**updates, "task_id": task_id},
            )
            await session.commit()

    async def get_task(self, task_id: str) -> dict | None:
        """获取任务详情"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("SELECT * FROM crawler_task WHERE task_id = :task_id"),
                {"task_id": task_id},
            )
            row = result.mappings().first()
            return dict(row) if row else None

    async def get_all_tasks(self) -> list[dict]:
        """获取所有任务"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("SELECT * FROM crawler_task ORDER BY created_at DESC LIMIT 100"),
            )
            return [dict(row) for row in result.mappings().all()]


class ReviewRepository:
    """审核数据仓库"""

    async def create_review(self, review: dict) -> int:
        """创建审核记录"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("""INSERT INTO station_review
                        (task_id, city_name, station_name, line_name, scraped_address, scraped_lat, scraped_lng, confidence, review_status, created_at)
                        VALUES (:task_id, :city_name, :station_name, :line_name, :scraped_address, :scraped_lat, :scraped_lng, :confidence, 'pending', NOW())"""),
                review,
            )
            await session.commit()
            return result.lastrowid

    async def get_pending(self, city: str = "", page: int = 1, size: int = 20) -> list[dict]:
        """获取待审核列表"""
        async with db_manager.get_session() as session:
            where = "WHERE review_status = 'pending'"
            params = {"limit": size, "offset": (page - 1) * size}
            if city:
                where += " AND city_name = :city"
                params["city"] = city
            result = await session.execute(
                text(f"SELECT * FROM station_review {where} ORDER BY created_at DESC LIMIT :limit OFFSET :offset"),
                params,
            )
            return [dict(row) for row in result.mappings().all()]

    async def approve(self, review_id: int, reviewer_id: int, note: str = ""):
        """批准审核"""
        async with db_manager.get_session() as session:
            await session.execute(
                text("""UPDATE station_review
                        SET review_status = 'approved', reviewer_id = :reviewer_id, review_note = :note, reviewed_at = NOW()
                        WHERE id = :id"""),
                {"id": review_id, "reviewer_id": reviewer_id, "note": note},
            )
            await session.commit()

    async def reject(self, review_id: int, reviewer_id: int, note: str = ""):
        """拒绝审核"""
        async with db_manager.get_session() as session:
            await session.execute(
                text("""UPDATE station_review
                        SET review_status = 'rejected', reviewer_id = :reviewer_id, review_note = :note, reviewed_at = NOW()
                        WHERE id = :id"""),
                {"id": review_id, "reviewer_id": reviewer_id, "note": note},
            )
            await session.commit()


# 全局单例
metro_repo = MetroRepository()
crawler_repo = CrawlerRepository()
review_repo = ReviewRepository()
