"""
测试配置
"""

import pytest
import asyncio
from unittest.mock import AsyncMock, MagicMock


@pytest.fixture(scope="session")
def event_loop():
    """创建事件循环"""
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()


@pytest.fixture
def mock_llm_gateway():
    """模拟 LLM 网关"""
    from app.gateway.schemas import LlmReply, LlmUsage
    gateway = AsyncMock()
    gateway.complete.return_value = LlmReply(
        content='{"intent": "route", "slots": {"from": "国贸", "to": "北京西站", "city": "北京"}}',
        usage=LlmUsage(input_tokens=100, output_tokens=50),
        provider="test",
        model="test-model",
    )
    return gateway


@pytest.fixture
def mock_java_client():
    """模拟 Java API 客户端"""
    client = AsyncMock()
    client.geocode.return_value = {"lat": 39.9042, "lng": 116.4074, "address": "北京市东城区"}
    client.regeo.return_value = {"lat": 39.9042, "lng": 116.4074, "address": "北京市东城区"}
    client.match_city.return_value = {"id": 1, "cityName": "北京市"}
    client.find_nearest_stations.return_value = [
        {"id": 1, "stationName": "国贸站"},
        {"id": 2, "stationName": "大望路站"},
    ]
    client.plan_route.return_value = {
        "stations": [
            {"stationName": "国贸站"},
            {"stationName": "大望路站"},
            {"stationName": "北京西站"},
        ],
        "transfers": [],
        "price": 4,
        "durationMinutes": 15,
    }
    return client


@pytest.fixture
def sample_crawl_result():
    """示例爬取结果"""
    from app.crawler.sources.base import CrawlResult, ScrapedLine, ScrapedStation
    return CrawlResult(
        source="test",
        city_name="测试城市",
        lines=[
            ScrapedLine(name="1号线", stations=["站A", "站B", "站C"]),
            ScrapedLine(name="2号线", stations=["站D", "站B", "站E"]),
        ],
        stations=[
            ScrapedStation(name="站A", line_name="1号线", lat=39.9, lng=116.4),
            ScrapedStation(name="站B", line_name="1号线;2号线", lat=39.91, lng=116.41, is_transfer=True),
            ScrapedStation(name="站C", line_name="1号线", lat=39.92, lng=116.42),
            ScrapedStation(name="站D", line_name="2号线", lat=39.89, lng=116.39),
            ScrapedStation(name="站E", line_name="2号线", lat=39.93, lng=116.43),
        ],
    )
