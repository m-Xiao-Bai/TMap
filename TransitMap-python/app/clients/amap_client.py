"""
高德地图 API 客户端

提供地理编码、逆地理编码、POI 搜索等功能。
"""

import logging
from dataclasses import dataclass

import httpx

from app.config import settings

logger = logging.getLogger("tmap-python.amap")


@dataclass
class GeoResult:
    """地理编码结果"""
    name: str
    lat: float
    lng: float
    address: str = ""
    city: str = ""
    district: str = ""
    adcode: str = ""


class AmapClient:
    """高德地图 API 客户端"""

    def __init__(self):
        self._client: httpx.AsyncClient | None = None

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                base_url=settings.AMAP_BASE_URL,
                timeout=httpx.Timeout(10),
            )
        return self._client

    async def geocode(self, address: str, city: str = "") -> GeoResult | None:
        """
        地理编码：地址 → 坐标

        Args:
            address: 地址，如 "南昌市八一广场"
            city: 城市，可选
        """
        client = await self._get_client()
        params = {
            "key": settings.AMAP_API_KEY,
            "address": address,
            "output": "JSON",
        }
        if city:
            params["city"] = city

        try:
            resp = await client.get("/v3/geocode/geo", params=params)
            data = resp.json()
            if data.get("status") != "1" or not data.get("geocodes"):
                logger.warning(f"高德 geocode 失败: {data.get('info', '未知错误')}")
                return None

            geo = data["geocodes"][0]
            location = geo.get("location", "").split(",")
            if len(location) != 2:
                return None

            return GeoResult(
                name=geo.get("formatted_address", address),
                lat=float(location[1]),
                lng=float(location[0]),
                address=geo.get("formatted_address", ""),
                city=geo.get("city", ""),
                district=geo.get("district", ""),
                adcode=geo.get("adcode", ""),
            )
        except Exception as e:
            logger.error(f"高德 geocode 异常: {e}")
            return None

    async def regeo(self, lng: float, lat: float) -> GeoResult | None:
        """
        逆地理编码：坐标 → 地址

        Args:
            lng: 经度
            lat: 纬度
        """
        client = await self._get_client()
        params = {
            "key": settings.AMAP_API_KEY,
            "location": f"{lng},{lat}",
            "output": "JSON",
        }

        try:
            resp = await client.get("/v3/geocode/regeo", params=params)
            data = resp.json()
            if data.get("status") != "1":
                return None

            regeo_data = data.get("regeocode", {})
            addr_component = regeo_data.get("addressComponent", {})

            return GeoResult(
                name=regeo_data.get("formatted_address", ""),
                lat=lat,
                lng=lng,
                address=regeo_data.get("formatted_address", ""),
                city=addr_component.get("city", ""),
                district=addr_component.get("district", ""),
                adcode=addr_component.get("adcode", ""),
            )
        except Exception as e:
            logger.error(f"高德 regeo 异常: {e}")
            return None

    async def place_search(
        self, keywords: str, city: str = "", lat: float = 0, lng: float = 0, radius: int = 3000
    ) -> list[GeoResult]:
        """
        POI 搜索

        Args:
            keywords: 搜索关键词
            city: 城市
            lat/lng: 中心点坐标（附近搜索）
            radius: 搜索半径（米）
        """
        client = await self._get_client()
        params = {
            "key": settings.AMAP_API_KEY,
            "keywords": keywords,
            "output": "JSON",
            "offset": 10,
        }
        if city:
            params["city"] = city
        if lat and lng:
            params["location"] = f"{lng},{lat}"
            params["radius"] = radius
            params["sortrule"] = "distance"

        try:
            resp = await client.get("/v3/place/text", params=params)
            data = resp.json()
            if data.get("status") != "1":
                return []

            results = []
            for poi in data.get("pois", []):
                location = poi.get("location", "").split(",")
                if len(location) != 2:
                    continue
                results.append(GeoResult(
                    name=poi.get("name", ""),
                    lat=float(location[1]),
                    lng=float(location[0]),
                    address=poi.get("address", ""),
                    city=poi.get("cityname", ""),
                    district=poi.get("adname", ""),
                ))
            return results
        except Exception as e:
            logger.error(f"高德 POI 搜索异常: {e}")
            return []

    async def batch_geocode(self, addresses: list[dict]) -> list[GeoResult | None]:
        """
        批量地理编码。

        Args:
            addresses: [{"address": "...", "city": "..."}]

        Returns:
            与输入等长的结果列表，失败的位置为 None
        """
        import asyncio
        results = []
        for item in addresses:
            result = await self.geocode(item["address"], item.get("city", ""))
            results.append(result)
            await asyncio.sleep(0.1)  # 限流：每秒最多 10 次
        return results

    async def close(self):
        if self._client and not self._client.is_closed:
            await self._client.aclose()


# 全局单例
amap_client = AmapClient()
