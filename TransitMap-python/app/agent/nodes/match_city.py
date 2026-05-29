"""
节点 4: 城市匹配

调用 Java 内部 API 匹配城市，确定是否同城。
"""

import logging
from app.agent.state import AgentState
from app.clients.java_api import java_client

logger = logging.getLogger("tmap-python.agent.match_city")


async def match_city_node(state: AgentState) -> dict:
    """
    城市匹配节点。

    根据 from/to 的坐标或地址匹配城市：
    - 同一城市 → SAME_CITY
    - 不同城市 → CROSS_CITY
    - 找不到城市 → NO_METRO
    """
    if state.get("short_circuit"):
        return {}

    from_geo = state.get("from_geo")
    to_geo = state.get("to_geo")
    llm_city = state.get("llm_inferred_city")

    from_city_id = None
    to_city_id = None
    scenario = "NO_METRO"

    # 尝试通过 LLM 推断的城市匹配
    if llm_city:
        try:
            city = await java_client.match_city(llm_city)
            if city and city.get("id"):
                from_city_id = city["id"]
                to_city_id = city["id"]
                logger.info(f"LLM 城市匹配: {llm_city} → city_id={city['id']}")
        except Exception as e:
            logger.warning(f"LLM 城市匹配失败: {e}")

    # 通过地址匹配
    if not from_city_id and from_geo and from_geo.get("address"):
        try:
            city = await java_client.match_city(from_geo["address"])
            if city and city.get("id"):
                from_city_id = city["id"]
                logger.info(f"from 地址城市匹配: {from_geo['address']} → city_id={city['id']}")
        except Exception as e:
            logger.warning(f"from 城市匹配失败: {e}")

    if not to_city_id and to_geo and to_geo.get("address"):
        try:
            city = await java_client.match_city(to_geo["address"])
            if city and city.get("id"):
                to_city_id = city["id"]
                logger.info(f"to 地址城市匹配: {to_geo['address']} → city_id={city['id']}")
        except Exception as e:
            logger.warning(f"to 城市匹配失败: {e}")

    # 判断场景
    if from_city_id and to_city_id:
        if from_city_id == to_city_id:
            scenario = "SAME_CITY"
        else:
            scenario = "CROSS_CITY"
    elif from_city_id or to_city_id:
        # 只有一个城市有匹配
        if not from_city_id:
            from_city_id = to_city_id
        else:
            to_city_id = from_city_id
        scenario = "SAME_CITY"
    else:
        scenario = "NO_METRO"

    logger.info(f"城市匹配结果: from_city={from_city_id}, to_city={to_city_id}, scenario={scenario}")

    return {
        "from_city_id": from_city_id,
        "to_city_id": to_city_id,
        "scenario": scenario,
        "short_circuit": scenario != "SAME_CITY",
    }
