"""
Agent 工具定义和执行器

定义 Agent 可用的工具，以及执行逻辑。
LLM Planner 决定调用哪些工具，Tool Executor 执行。
"""

import logging
from app.clients.java_api import java_client
from app.clients.amap_client import amap_client

logger = logging.getLogger("tmap-python.agent.tools")


# 工具定义（给 LLM 看的）
TOOLS_DESCRIPTION = """
你可以使用以下工具：

1. geocode(address, city) - 地址转坐标
   - 输入: 地址（如"南昌西站"）、城市（如"南昌市"）
   - 输出: {lat, lng, address, city}

2. regeo(lat, lng) - 坐标转地址
   - 输入: 经纬度
   - 输出: {address, city}

3. ip_locate() - IP 定位
   - 输入: 无
   - 输出: {lat, lng, city}

4. city_match(name) - 城市匹配
   - 输入: 城市名
   - 输出: {id, cityName, ...} 或 null

5. find_nearest_stations(lat, lng, city_id, limit) - 查找最近站点
   - 输入: 坐标、城市ID、数量
   - 输出: [{id, stationName, distance}, ...]

6. get_stations_by_city(city_id) - 获取城市所有站点
   - 输入: 城市ID
   - 输出: [{id, stationName, lineNames}, ...]

7. plan_route(from_station_id, to_station_id) - 路径规划
   - 输入: 出发站ID、到达站ID
   - 输出: {stations, transfers, price, durationMinutes}

8. poi_search(keywords, city, lat, lng) - POI搜索
   - 输入: 关键词、城市、坐标
   - 输出: [{name, address, lat, lng}, ...]
"""


class ToolExecutor:
    """工具执行器"""

    async def execute(self, tool_name: str, args: dict) -> dict:
        """执行工具"""
        try:
            handler = getattr(self, f"_tool_{tool_name}", None)
            if not handler:
                return {"error": f"未知工具: {tool_name}"}
            return await handler(**args)
        except Exception as e:
            logger.error(f"工具执行失败 [{tool_name}]: {e}")
            return {"error": str(e)}

    async def _tool_geocode(self, address: str, city: str = "") -> dict:
        """地址转坐标"""
        # 如果地址不包含城市名，加上城市前缀
        if city and not any(c in address for c in ["市", "省"]):
            full_address = f"{city}{address}"
        else:
            full_address = address

        result = await java_client.geocode(full_address, city)
        if result:
            return {
                "lat": result.get("lat"),
                "lng": result.get("lng"),
                "address": result.get("address", address),
                "city": result.get("city", city),
            }
        return {"error": f"地理编码失败: {address}"}

    async def _tool_regeo(self, lat: float, lng: float) -> dict:
        """坐标转地址"""
        result = await java_client.regeo(lng, lat)
        if result:
            return {
                "address": result.get("address", ""),
                "city": result.get("city", ""),
            }
        return {"error": "逆地理编码失败"}

    async def _tool_ip_locate(self) -> dict:
        """IP 定位"""
        result = await java_client.ip_locate()
        if result:
            return {
                "lat": result.get("lat"),
                "lng": result.get("lng"),
                "city": result.get("city", ""),
            }
        return {"error": "IP 定位失败"}

    async def _tool_city_match(self, name: str) -> dict:
        """城市匹配"""
        result = await java_client.match_city(name)
        if result:
            return {
                "id": result.get("id"),
                "cityName": result.get("cityName"),
                "countryName": result.get("countryName"),
            }
        return {"result": None, "message": f"未找到城市: {name}"}

    async def _tool_find_nearest_stations(
        self, lat: float, lng: float, city_id: int, limit: int = 5
    ) -> dict:
        """查找最近站点"""
        result = await java_client.find_nearest_stations(lat, lng, city_id, limit)
        if result:
            return {"stations": result}
        return {"stations": [], "message": "未找到附近站点"}

    async def _tool_get_stations_by_city(self, city_id: int) -> dict:
        """获取城市所有站点"""
        result = await java_client.get_stations_by_city(city_id)
        if result:
            return {"stations": result}
        return {"stations": [], "message": "该城市暂无站点数据"}

    async def _tool_plan_route(self, from_station_id: int, to_station_id: int) -> dict:
        """路径规划"""
        result = await java_client.plan_route(from_station_id, to_station_id)
        if result:
            return result
        return {"error": "路径规划失败"}

    async def _tool_get_station(self, station_id: int) -> dict:
        """获取站点详情"""
        result = await java_client.get_station(station_id)
        if result:
            return result
        return {"error": f"站点不存在: {station_id}"}

    async def _tool_poi_search(
        self, keywords: str, city: str = "", lat: float = 0, lng: float = 0
    ) -> dict:
        """POI 搜索"""
        result = await java_client.poi_search(keywords, city, lat, lng)
        if result:
            return {"pois": result}
        return {"pois": [], "message": "未找到相关 POI"}

    async def _tool_create_order(
        self, user_id: int, start_station_id: int, end_station_id: int, quantity: int = 1
    ) -> dict:
        """创建订单"""
        result = await java_client.create_order(user_id, start_station_id, end_station_id, quantity)
        if result:
            return result
        return {"error": "创建订单失败"}
