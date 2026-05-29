"""
爬虫服务 — 数据模型
"""

from pydantic import BaseModel
from typing import Optional
from datetime import datetime


# ── 请求模型 ──

class CrawlerTriggerRequest(BaseModel):
    """触发爬取请求"""
    city_name: str
    country_id: int = 1
    sources: str = "wikipedia,baike"  # 逗号分隔


class CrawlerBatchRequest(BaseModel):
    """批量爬取请求"""
    cities: list[CrawlerTriggerRequest]


class ReviewActionRequest(BaseModel):
    """审核操作请求"""
    review_id: int
    action: str  # "approve" | "reject"
    note: str = ""


class BatchReviewRequest(BaseModel):
    """批量审核请求"""
    review_ids: list[int]
    action: str  # "approve" | "reject"
    note: str = ""


# ── 响应模型 ──

class CrawlerTaskResponse(BaseModel):
    """爬取任务状态"""
    task_id: str
    city_name: str
    status: str  # pending/running/completed/failed/cancelled
    progress_pct: int = 0
    current_step: str = ""
    lines_found: int = 0
    stations_found: int = 0
    lines_inserted: int = 0
    lines_updated: int = 0
    stations_inserted: int = 0
    stations_updated: int = 0
    stations_skipped: int = 0
    stations_pending_review: int = 0
    error_message: str = ""
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    created_at: Optional[datetime] = None


class CrawlReport(BaseModel):
    """爬取报告"""
    task_id: str
    city_name: str
    lines_found: int = 0
    stations_found: int = 0
    lines_inserted: int = 0
    lines_updated: int = 0
    stations_inserted: int = 0
    stations_updated: int = 0
    stations_skipped: int = 0
    stations_pending_review: int = 0
    data_quality_score: float = 0.0
    source_comparison: dict = {}
    errors: list[str] = []


class StationReviewResponse(BaseModel):
    """审核项"""
    id: int
    task_id: str
    city_name: str
    station_name: str
    line_name: str = ""
    scraped_address: str = ""
    scraped_lat: Optional[float] = None
    scraped_lng: Optional[float] = None
    review_status: str = "pending"
    confidence: str = ""
    review_note: str = ""
    created_at: Optional[datetime] = None


# ── 内部模型 ──

class ScrapedLine(BaseModel):
    """爬取到的线路数据"""
    name: str
    color: str = ""
    stations: list[str] = []  # 站点名称列表（按顺序）


class ScrapedStation(BaseModel):
    """爬取到的站点数据"""
    name: str
    line_name: str
    order: int = 0  # 在线路上的顺序
    is_transfer: bool = False
    address: str = ""
    lat: float = 0.0
    lng: float = 0.0
    confidence: str = "medium"


class CrawlResult(BaseModel):
    """单个数据源的爬取结果"""
    source: str  # "wikipedia" | "baike"
    city_name: str
    lines: list[ScrapedLine] = []
    stations: list[ScrapedStation] = []
    success: bool = True
    error: str = ""


class ComparisonResult(BaseModel):
    """双源对比结果"""
    wiki_result: Optional[CrawlResult] = None
    baike_result: Optional[CrawlResult] = None
    merged_lines: list[ScrapedLine] = []
    merged_stations: list[ScrapedStation] = []
    wiki_only_stations: list[str] = []
    baike_only_stations: list[str] = []
    conflict_stations: list[str] = []
    confidence_summary: dict = {}  # {"high": 50, "medium": 30, "low": 10}
