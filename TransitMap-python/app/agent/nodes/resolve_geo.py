"""
节点 3: 地理编码

调用 Java 内部 API 进行地理编码：
- from/to 地址 → 坐标
- GPS 坐标 → 逆地理编码
- IP 定位兜底
"""

import logging
from app.agent.state import AgentState
from app.clients.java_api import java_client

logger = logging.getLogger("tmap-python.agent.resolve_geo")


async def resolve_geo_node(state: AgentState) -> dict:
    """
    地理编码节点。

    为 from/to 获取坐标：
    1. 如果有地址 → geocode 获取坐标
    2. 如果 from 为空 → 用 GPS 坐标 regeo 获取地址
    3. 如果 GPS 也为空 → IP 定位
    """
    slot_from = state.get("slot_from")
    slot_to = state.get("slot_to")
    lat = state.get("lat", 0)
    lng = state.get("lng", 0)
    llm_city = state.get("llm_inferred_city")

    from_geo = None
    to_geo = None

    # 处理出发地
    if slot_from:
        # 有地址，geocode
        try:
            result = await java_client.geocode(slot_from, llm_city or "")
            if result:
                from_geo = {"lat": result.get("lat"), "lng": result.get("lng"), "address": result.get("address", slot_from)}
                logger.info(f"geocode from: {slot_from} → ({from_geo['lat']}, {from_geo['lng']})")
        except Exception as e:
            logger.warning(f"geocode from 失败: {e}")
    elif lat != 0 and lng != 0:
        # 无地址但有 GPS，regeo
        try:
            result = await java_client.regeo(lng, lat)
            if result:
                from_geo = {"lat": lat, "lng": lng, "address": result.get("address", "")}
                slot_from = result.get("address", "")
                logger.info(f"regeo from: ({lat}, {lng}) → {slot_from}")
        except Exception as e:
            logger.warning(f"regeo from 失败: {e}")
    else:
        # 无地址无 GPS，IP 定位
        try:
            result = await java_client.ip_locate()
            if result and result.get("lat") and result.get("lng"):
                from_geo = {"lat": result["lat"], "lng": result["lng"], "address": result.get("address", "")}
                slot_from = result.get("address", "")
                logger.info(f"ip-locate from: {slot_from}")
        except Exception as e:
            logger.warning(f"ip-locate from 失败: {e}")

    # 处理目的地
    if slot_to:
        try:
            result = await java_client.geocode(slot_to, llm_city or "")
            if result:
                to_geo = {"lat": result.get("lat"), "lng": result.get("lng"), "address": result.get("address", slot_to)}
                logger.info(f"geocode to: {slot_to} → ({to_geo['lat']}, {to_geo['lng']})")
        except Exception as e:
            logger.warning(f"geocode to 失败: {e}")

    # 检查是否有缺失
    scenario = None
    if not slot_to:
        scenario = "MISSING_DEST"
    elif not from_geo or not to_geo:
        # 地理编码失败
        if not from_geo and not to_geo:
            scenario = "NO_ROUTE"
        elif not from_geo:
            scenario = "MISSING_DEST"

    return {
        "slot_from": slot_from,
        "slot_to": slot_to,
        "from_geo": from_geo,
        "to_geo": to_geo,
        "scenario": scenario,
        "short_circuit": scenario is not None,
    }
