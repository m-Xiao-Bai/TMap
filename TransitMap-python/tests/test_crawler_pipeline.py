"""
爬虫管线测试
"""

import pytest
from app.crawler.sources.base import CrawlResult, ScrapedLine, ScrapedStation
from app.crawler.sources.comparator import SourceComparator, normalize_name


class TestNormalizeName:
    """名称标准化测试"""

    def test_removes_station_suffix(self):
        assert normalize_name("国贸站") == "国贸"

    def test_removes_parentheses(self):
        assert normalize_name("国贸(东)") == "国贸"

    def test_removes_spaces(self):
        assert normalize_name("北京 西站") == "北京西站"

    def test_empty_string(self):
        assert normalize_name("") == ""

    def test_case_insensitive(self):
        assert normalize_name("Guomao") == "guomao"


class TestSourceComparator:
    """多源对比器测试"""

    def setup_method(self):
        self.comparator = SourceComparator()

    def test_compare_empty_results(self):
        result = self.comparator.compare([])
        assert result.merged_stations == []
        assert result.merged_lines == []

    def test_compare_single_source(self):
        crawl = CrawlResult(
            source="test",
            city_name="测试城市",
            stations=[
                ScrapedStation(name="站A", line_name="1号线", lat=39.9, lng=116.4),
            ],
        )
        result = self.comparator.compare([crawl])
        assert len(result.merged_stations) == 1
        assert result.merged_stations[0].confidence == "low"  # 单源 = low

    def test_compare_two_sources_same_station(self):
        crawl1 = CrawlResult(
            source="wiki",
            city_name="测试城市",
            stations=[ScrapedStation(name="站A", line_name="1号线", lat=39.9, lng=116.4)],
        )
        crawl2 = CrawlResult(
            source="osm",
            city_name="测试城市",
            stations=[ScrapedStation(name="站A", line_name="1号线", lat=39.9, lng=116.4, osmid=12345)],
        )
        result = self.comparator.compare([crawl1, crawl2])
        assert len(result.merged_stations) == 1
        assert result.merged_stations[0].confidence == "medium"  # 双源 = medium
        assert 12345 in [s.osmid for s in result.merged_stations]

    def test_compare_three_sources(self):
        crawl1 = CrawlResult(source="wiki", city_name="测试",
                           stations=[ScrapedStation(name="站A", lat=39.9, lng=116.4)])
        crawl2 = CrawlResult(source="baike", city_name="测试",
                           stations=[ScrapedStation(name="站A", lat=39.9, lng=116.4)])
        crawl3 = CrawlResult(source="osm", city_name="测试",
                           stations=[ScrapedStation(name="站A", lat=39.9, lng=116.4, osmid=123)])
        result = self.comparator.compare([crawl1, crawl2, crawl3])
        assert len(result.merged_stations) == 1
        assert result.merged_stations[0].confidence == "high"  # 三源 = high

    def test_compare_different_stations(self):
        crawl1 = CrawlResult(source="wiki", city_name="测试",
                           stations=[ScrapedStation(name="站A", lat=39.9, lng=116.4)])
        crawl2 = CrawlResult(source="baike", city_name="测试",
                           stations=[ScrapedStation(name="站B", lat=39.91, lng=116.41)])
        result = self.comparator.compare([crawl1, crawl2])
        assert len(result.merged_stations) == 2

    def test_failed_source_filtered(self):
        crawl1 = CrawlResult(source="wiki", city_name="测试",
                           stations=[ScrapedStation(name="站A")])
        crawl2 = CrawlResult(source="baike", city_name="测试",
                           success=False, error="爬取失败")
        result = self.comparator.compare([crawl1, crawl2])
        assert len(result.merged_stations) == 1


class TestQualityChecker:
    """数据质检测试"""

    def test_haversine(self):
        from app.crawler.pipeline.quality_checker import haversine
        # 北京天安门到故宫北门约 1km
        dist = haversine(39.9042, 116.3975, 39.9200, 116.3975)
        assert 1000 < dist < 2000  # 约 1.7km

    @pytest.mark.asyncio
    async def test_check_quality(self):
        from app.crawler.pipeline.quality_checker import check_quality
        from app.crawler.sources.comparator import MergedStation, MergedLine

        stations = [
            MergedStation(name="站A", lat=39.9, lng=116.4, confidence="high"),
            MergedStation(name="站B", lat=39.91, lng=116.41, confidence="high"),
        ]
        lines = [MergedLine(name="1号线", stations=["站A", "站B"])]

        report = await check_quality("test-task", "测试城市", stations, lines)
        assert report.error_count == 0
        assert report.quality_score > 0.8

    @pytest.mark.asyncio
    async def test_out_of_range_coords(self):
        from app.crawler.pipeline.quality_checker import check_quality
        from app.crawler.sources.comparator import MergedStation, MergedLine

        stations = [
            MergedStation(name="站A", lat=100.0, lng=200.0, confidence="high"),  # 超出范围
        ]
        lines = []

        report = await check_quality("test-task", "测试城市", stations, lines)
        assert report.error_count > 0
