"""
Java 内部 API 客户端

调用 Java 后端暴露的内部 API，获取数据库查询、高德 API、路径规划等服务。
"""

import logging
from typing import Any

import httpx

from app.config import settings
from app.exceptions import JavaApiError

logger = logging.getLogger("tmap-python.java-api")


class JavaApiClient:
    """Java 内部 API 客户端"""

    def __init__(self):
        self._client: httpx.AsyncClient | None = None

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                base_url=settings.JAVA_INTERNAL_API_URL,
                headers={
                    "X-API-Key": settings.JAVA_INTERNAL_API_KEY,
                    "Content-Type": "application/json",
                },
                timeout=httpx.Timeout(settings.JAVA_INTERNAL_TIMEOUT),
            )
        return self._client

    async def _request(
        self,
        method: str,
        path: str,
        params: dict = None,
        json_data: dict = None,
    ) -> Any:
        """通用请求方法"""
        client = await self._get_client()
        try:
            resp = await client.request(method, path, params=params, json=json_data)
            resp.raise_for_status()
            data = resp.json()
            if data.get("code") != 200:
                raise JavaApiError(
                    f"Java API 返回错误: {data.get('message', '未知错误')}",
                    endpoint=path,
                )
            return data.get("data")
        except httpx.HTTPStatusError as e:
            raise JavaApiError(
                f"Java API HTTP 错误: {e.response.status_code}",
                endpoint=path,
            )
        except JavaApiError:
            raise
        except Exception as e:
            raise JavaApiError(f"Java API 调用失败: {e}", endpoint=path)

    # ── 地理编码 ──

    async def geocode(self, address: str, city: str = "") -> dict | None:
        """地址 → 坐标"""
        data = {"address": address}
        if city:
            data["city"] = city
        return await self._request("POST", "/geo/geocode", json_data=data)

    async def regeo(self, lng: float, lat: float) -> dict | None:
        """坐标 → 地址"""
        return await self._request("POST", "/geo/regeo", json_data={"lng": lng, "lat": lat})

    async def ip_locate(self, ip: str = "") -> dict | None:
        """IP 定位"""
        params = {}
        if ip:
            params["ip"] = ip
        return await self._request("GET", "/geo/ip-locate", params=params)

    async def poi_search(self, keywords: str, city: str = "", lat: float = 0, lng: float = 0) -> list:
        """POI 搜索"""
        data = {"keywords": keywords}
        if city:
            data["city"] = city
        if lat and lng:
            data["lat"] = lat
            data["lng"] = lng
        return await self._request("POST", "/geo/poi-search", json_data=data)

    # ── 城市 ──

    async def match_city(self, name: str) -> dict | None:
        """城市名称匹配"""
        return await self._request("GET", "/city/match", params={"name": name})

    async def get_city(self, city_id: int) -> dict | None:
        """获取城市详情"""
        return await self._request("GET", f"/city/{city_id}")

    # ── 站点 ──

    async def find_nearest_stations(
        self, lat: float, lng: float, city_id: int, limit: int = 5
    ) -> list:
        """查找最近站点"""
        return await self._request("GET", "/station/nearest", params={
            "lat": lat, "lng": lng, "cityId": city_id, "limit": limit,
        })

    async def get_stations_by_city(self, city_id: int) -> list:
        """获取城市所有站点"""
        return await self._request("GET", "/station/by-city", params={"cityId": city_id})

    async def get_station(self, station_id: int) -> dict | None:
        """获取站点详情"""
        return await self._request("GET", f"/station/{station_id}")

    # ── 路径规划 ──

    async def plan_route(self, from_station_id: int, to_station_id: int) -> dict | None:
        """路径规划"""
        return await self._request("POST", "/route/plan", json_data={
            "fromStationId": from_station_id,
            "toStationId": to_station_id,
        })

    # ── 订单 ──

    async def create_order(
        self, user_id: int, start_station_id: int, end_station_id: int, quantity: int = 1
    ) -> dict | None:
        """创建订单"""
        return await self._request("POST", "/order/create", json_data={
            "userId": user_id,
            "startStationId": start_station_id,
            "endStationId": end_station_id,
            "quantity": quantity,
        })

    # ── 会话 ──

    async def get_session_history(self, session_id: int, limit: int = 10) -> list:
        """获取会话历史"""
        return await self._request("GET", f"/session/{session_id}/history", params={"limit": limit})

    # ── 配置 ──

    async def get_agent_prompt(self, key: str) -> str | None:
        """获取 Agent prompt 配置"""
        data = await self._request("GET", "/config/agent-prompt", params={"key": key})
        return data.get("value") if data else None

    async def ping(self) -> bool:
        """健康检查"""
        try:
            await self._request("GET", "/health")
            return True
        except Exception:
            return False

    async def close(self):
        """关闭连接"""
        if self._client and not self._client.is_closed:
            await self._client.aclose()


# 全局单例
java_client = JavaApiClient()
