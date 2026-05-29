"""
节点: 下单处理

处理用户的下单请求。
"""

import logging
from app.agent.state import AgentState
from app.clients.java_api import java_client

logger = logging.getLogger("tmap-python.agent.handle_order")


async def handle_order_node(state: AgentState) -> dict:
    """
    下单处理节点。

    1. 从对话历史中找到最近的路线卡片
    2. 调用 Java API 创建订单
    3. 返回订单结果
    """
    user_id = state.get("user_id")
    chat_history = state.get("chat_history", [])

    if not user_id:
        return {
            "reply": "请先登录后再下单 🚇",
            "chips": ["查看路线", "登录"],
        }

    # 从历史中找路线卡片
    route_plan = None
    for msg in reversed(chat_history):
        extras = msg.get("extras", {})
        if isinstance(extras, str):
            import json
            try:
                extras = json.loads(extras)
            except:
                extras = {}
        if extras.get("kind") == "ROUTE_CARD":
            route_plan = extras.get("payload", {})
            break

    if not route_plan:
        return {
            "reply": "没有找到路线信息，请先规划路线再下单 🚇",
            "chips": ["我要去...", "附近地铁"],
        }

    # 提取站点 ID
    start_station_id = route_plan.get("startStationId")
    end_station_id = route_plan.get("endStationId")

    if not start_station_id or not end_station_id:
        return {
            "reply": "路线信息不完整，无法下单。请重新规划路线 🚇",
            "chips": ["重新规划"],
        }

    # 创建订单
    try:
        order = await java_client.create_order(
            user_id=user_id,
            start_station_id=start_station_id,
            end_station_id=end_station_id,
            quantity=1,
        )
        if order:
            logger.info(f"订单创建成功: {order}")
            return {
                "reply": "下单成功！请查看订单详情 🚇",
                "cards": [{"kind": "ORDER_CARD", "payload": order}],
                "chips": ["查看订单", "继续规划"],
            }
    except Exception as e:
        logger.error(f"下单失败: {e}")

    return {
        "reply": "下单失败，请稍后再试 🚇",
        "chips": ["重新下单", "查看路线"],
    }
