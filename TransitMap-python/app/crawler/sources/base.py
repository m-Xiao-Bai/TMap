"""
爬虫数据源 — 抽象基类
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field


@dataclass
class ScrapedLine:
    """爬取到的线路数据"""
    name: str
    name_en: str = ""
    color: str = ""
    stations: list[str] = field(default_factory=list)  # 站点名称列表（按顺序）


@dataclass
class ScrapedStation:
    """爬取到的站点数据"""
    name: str
    name_en: str = ""
    alias: str = ""
    line_name: str = ""
    order: int = 0
    is_transfer: bool = False
    address: str = ""
    lat: float = 0.0
    lng: float = 0.0
    osmid: int = 0


@dataclass
class CrawlResult:
    """单个数据源的爬取结果"""
    source: str           # "wikipedia" | "baike" | "osm"
    city_name: str
    lines: list[ScrapedLine] = field(default_factory=list)
    stations: list[ScrapedStation] = field(default_factory=list)
    success: bool = True
    error: str = ""


class BaseSource(ABC):
    """数据源抽象接口"""

    @property
    @abstractmethod
    def name(self) -> str:
        """数据源名称"""
        ...

    @abstractmethod
    async def crawl(self, city_name: str) -> CrawlResult:
        """
        爬取指定城市的轨道交通数据。

        Args:
            city_name: 城市名称，如 "南昌市", "北京"

        Returns:
            CrawlResult 包含线路和站点数据
        """
        ...
