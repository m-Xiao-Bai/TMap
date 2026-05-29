"""
节点 5: 路径规划

调用 Java 内部 API 查找最近站点并规划路线。
"""

import logging
from app.agent.state import AgentState
from app.clients.java_api import java_client

logger = logging.getLogger("tmap-python.agent.plan_route")


async def plan_route_node(state: AgentState) -> dict:
    """
    路径规划节点。

    1. 查找 from/to 最近的地铁站
    2. 调用 BFS 路径规划
    3. 返回路线结果
    """
    if state.get("short_circuit"):
        return {}

    from_geo = state.get("from_geo")
    to_geo = state.get("to_geo")
    city_id = state.get("from_city_id")

    if not from_geo or not to_geo or not city_id:
        return {"scenario": "NO_ROUTE", "short_circuit": True}

    # 查找最近站点
    from_station = None
    to_station = None

    try:
        from_stations = await java_client.find_nearest_stations(
            from_geo["lat"], from_geo["lng"], city_id, limit=3
        )
        if from_stations:
            from_station = from_stations[0]
            logger.info(f"最近出发站: {from_station.get('stationName')} (ID={from_station.get('id')})")
    except Exception as e:
        logger.warning(f"查找出发站失败: {e}")

    try:
        to_stations = await java_client.find_nearest_stations(
            to_geo["lat"], to_geo["lng"], city_id, limit=3
        )
        if to_stations:
            to_station = to_stations[0]
            logger.info(f"最近到达站: {to_station.get('stationName')} (ID={to_station.get('id')})")
    except Exception as e:
        logger.warning(f"查找到达站失败: {e}")

    if not from_station or not to_station:
        return {"scenario": "NO_ROUTE", "short_circuit": True}

    # 检查是否同一站点
    if from_station.get("id") == to_station.get("id"):
        return {
            "from_station": from_station,
            "to_station": to_station,
            "scenario": "SAME_STATION",
            "short_circuit": True,
        }

    # 路径规划
    try:
        route = await java_client.plan_route(
            from_station.get("id"),
            to_station.get("id"),
        )
        if route:
            logger.info(f"路径规划成功: {len(route.get('stations', []))} 站")
            return {
                "from_station": from_station,
                "to_station": to_station,
                "route_plan": route,
                "scenario": "SAME_CITY",
            }
    except Exception as e:
        logger.warning(f"路径规划失败: {e}")

    return {"scenario": "NO_ROUTE", "short_circuit": True}
