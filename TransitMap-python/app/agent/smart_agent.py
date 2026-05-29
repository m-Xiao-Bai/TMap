"""
智能 Agent 主流程

核心思想：
- LLM 只负责理解意图和生成回复
- 工具调用由代码控制，不让 LLM 写占位符
- 上下文记忆，智能纠错
"""

import logging
from app.agent.context import ConversationContext
from app.agent.planner import plan
from app.agent.tools import ToolExecutor
from app.agent.responder import generate_route_reply, generate_chat_reply

logger = logging.getLogger("tmap-python.agent.smart")

tool_executor = ToolExecutor()


async def process_message(request_data: dict) -> dict:
    """
    处理用户消息。

    流程:
    1. 构建上下文
    2. LLM 理解意图
    3. 代码执行工具（不让 LLM 写参数）
    4. 生成回复
    """
    # 1. 构建上下文
    ctx = ConversationContext()
    await ctx.build_from_request(request_data)

    logger.info(f"上下文: city={ctx.current_city}, recent_from={ctx.recent_from}, recent_to={ctx.recent_to}")

    # 2. LLM 理解意图
    planner_result = await plan(
        user_message=ctx.user_message,
        context=ctx.to_dict(),
        chat_history=ctx.chat_history,
    )

    # 更新上下文
    if planner_result.city:
        ctx.current_city = planner_result.city
    if planner_result.from_location:
        ctx.recent_from = planner_result.from_location
    if planner_result.to_location:
        ctx.recent_to = planner_result.to_location

    logger.info(f"意图: {planner_result.intent}, city={ctx.current_city}, from={ctx.recent_from}, to={ctx.recent_to}")

    # 3. 根据意图分发
    if planner_result.intent == "route":
        return await _handle_route(ctx)

    if planner_result.intent == "order":
        return await _handle_order(ctx)

    if planner_result.intent == "clarification":
        return {
            "reply": planner_result.clarification or "请告诉我更多信息",
            "cards": [],
            "chips": _get_clarification_chips(ctx),
            "tokens_in": 0,
            "tokens_out": 0,
        }

    # 默认：通用对话
    return await _handle_chat(ctx)


async def _handle_route(ctx: ConversationContext) -> dict:
    """处理路线规划（代码控制流程，不让 LLM 写参数）"""

    # 1. 确定出发地和目的地
    from_addr = ctx.recent_from
    to_addr = ctx.recent_to

    if not from_addr and not to_addr:
        return {
            "reply": "请告诉我你从哪里出发，要去哪里？",
            "cards": [],
            "chips": ["从当前位置出发", "查看附近站点"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    if not to_addr:
        return {
            "reply": "你想去哪里呢？告诉我目的地 🚇",
            "cards": [],
            "chips": ["去南昌站", "去机场", "去..."],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    # 2. 确定城市
    city = ctx.current_city
    city_id = ctx.current_city_id

    if not city:
        return {
            "reply": "请告诉我你在哪个城市？",
            "cards": [],
            "chips": ["南昌市", "北京市", "上海市"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    # 匹配城市
    if not city_id:
        city_result = await tool_executor.execute("city_match", {"name": city})
        if city_result.get("id"):
            city_id = city_result["id"]
            ctx.current_city_id = city_id
        else:
            return {
                "reply": f"抱歉，暂未找到「{city}」的地铁数据。你可以通知管理员添加该城市 🚇",
                "cards": [],
                "chips": [f"::cmd:notify_admin:请求添加{city}地铁数据|通知管理员添加"],
                "tokens_in": 0,
                "tokens_out": 0,
            }

    # 3. 地理编码（带城市上下文）
    from_geo = await _geocode_with_city(from_addr, city)
    to_geo = await _geocode_with_city(to_addr, city)

    if not from_geo:
        return {
            "reply": f"抱歉，找不到「{from_addr}」的位置。请确认地址是否正确 🚇",
            "cards": [],
            "chips": ["重新规划", "查看附近站点"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    if not to_geo:
        return {
            "reply": f"抱歉，找不到「{to_addr}」的位置。请确认地址是否正确 🚇",
            "cards": [],
            "chips": ["重新规划", "查看附近站点"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    # 4. 查找最近站点
    from_stations = await tool_executor.execute("find_nearest_stations", {
        "lat": from_geo["lat"], "lng": from_geo["lng"],
        "city_id": city_id, "limit": 3,
    })
    to_stations = await tool_executor.execute("find_nearest_stations", {
        "lat": to_geo["lat"], "lng": to_geo["lng"],
        "city_id": city_id, "limit": 3,
    })

    from_list = from_stations.get("stations", [])
    to_list = to_stations.get("stations", [])

    if not from_list:
        return {
            "reply": f"抱歉，「{from_addr}」附近没有找到地铁站。可能是该区域暂未开通地铁 🚇",
            "cards": [],
            "chips": ["查看附近站点", "重新规划", f"::cmd:notify_admin:{city}{from_addr}附近缺少地铁站数据|通知管理员"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    if not to_list:
        return {
            "reply": f"抱歉，「{to_addr}」附近没有找到地铁站。可能是该区域暂未开通地铁 🚇",
            "cards": [],
            "chips": ["查看附近站点", "重新规划", f"::cmd:notify_admin:{city}{to_addr}附近缺少地铁站数据|通知管理员"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    # 5. 路径规划
    from_station = from_list[0]
    to_station = to_list[0]

    if from_station.get("id") == to_station.get("id"):
        return {
            "reply": "出发站和到达站是同一个站，不需要乘坐地铁 🚇",
            "cards": [],
            "chips": ["查看附近站点", "重新规划"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    route_result = await tool_executor.execute("plan_route", {
        "from_station_id": from_station["id"],
        "to_station_id": to_station["id"],
    })

    if route_result.get("error"):
        return {
            "reply": "抱歉，路径规划失败。请稍后再试 🚇",
            "cards": [],
            "chips": ["重新规划", "联系客服"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    # 6. 生成回复
    reply_result = await generate_route_reply(
        user_message=ctx.user_message,
        route_plan=route_result,
        from_station=from_station,
        to_station=to_station,
    )

    # 更新上下文
    ctx.update_after_route(route_result)

    return {
        "reply": reply_result["reply"],
        "cards": reply_result.get("cards", []),
        "chips": reply_result.get("chips", []),
        "tokens_in": 0,
        "tokens_out": 0,
    }


async def _handle_order(ctx: ConversationContext) -> dict:
    """处理下单（带数据校验）"""
    if not ctx.user_id:
        return {
            "reply": "请先登录后再下单 🚇",
            "cards": [],
            "chips": ["查看路线", "登录"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    if not ctx.last_route_plan:
        return {
            "reply": "没有找到路线信息，请先规划路线再下单 🚇",
            "cards": [],
            "chips": ["我要去...", "附近地铁"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    # 从路线卡片中提取站点 ID
    start_station_id = ctx.last_route_plan.get("startStationId")
    end_station_id = ctx.last_route_plan.get("endStationId")

    if not start_station_id or not end_station_id:
        return {
            "reply": "路线信息不完整，无法下单。请重新规划路线 🚇",
            "cards": [],
            "chips": ["重新规划"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    # 校验站点是否存在
    start_station = await tool_executor.execute("get_station", {"station_id": start_station_id})
    if start_station.get("error"):
        return {
            "reply": "出发站信息不存在，可能是数据未同步。请重新规划路线或通知管理员 🚇",
            "cards": [],
            "chips": ["重新规划", "::cmd:notify_admin:出发站数据缺失|通知管理员"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    end_station = await tool_executor.execute("get_station", {"station_id": end_station_id})
    if end_station.get("error"):
        return {
            "reply": "到达站信息不存在，可能是数据未同步。请重新规划路线或通知管理员 🚇",
            "cards": [],
            "chips": ["重新规划", "::cmd:notify_admin:到达站数据缺失|通知管理员"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    # 校验路线是否有效
    route_check = await tool_executor.execute("plan_route", {
        "from_station_id": start_station_id,
        "to_station_id": end_station_id,
    })
    if route_check.get("error"):
        return {
            "reply": "路线规划已失效，请重新规划路线 🚇",
            "cards": [],
            "chips": ["重新规划"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    # 创建订单
    order_result = await tool_executor.execute("create_order", {
        "user_id": ctx.user_id,
        "start_station_id": start_station_id,
        "end_station_id": end_station_id,
        "quantity": 1,
    })

    if order_result.get("error"):
        return {
            "reply": "下单失败，请稍后再试 🚇",
            "cards": [],
            "chips": ["重新下单", "查看路线"],
            "tokens_in": 0,
            "tokens_out": 0,
        }

    return {
        "reply": "下单成功！请查看订单详情 🚇",
        "cards": [{"kind": "ORDER_CARD", "payload": order_result}],
        "chips": ["查看订单", "继续规划"],
        "tokens_in": 0,
        "tokens_out": 0,
    }


async def _handle_chat(ctx: ConversationContext) -> dict:
    """处理通用对话"""
    chat_result = await generate_chat_reply(
        user_message=ctx.user_message,
        context=ctx.to_dict(),
        chat_history=ctx.chat_history,
    )
    return {
        "reply": chat_result["reply"],
        "cards": [],
        "chips": chat_result.get("chips", []),
        "tokens_in": 0,
        "tokens_out": 0,
    }


async def _geocode_with_city(address: str, city: str) -> dict | None:
    """带城市上下文的地理编码，失败时自动重试"""
    if not address:
        return None

    # 第一次尝试
    result = await tool_executor.execute("geocode", {"address": address, "city": city})

    if result.get("lat"):
        # 校验城市是否匹配
        result_city = result.get("city", "")
        if city and city not in result_city and result_city not in city:
            # 城市不匹配，用"城市+地址"重试
            logger.warning(f"geocode 城市不匹配: 期望={city}, 实际={result_city}, 重试")
            retry = await tool_executor.execute("geocode", {
                "address": f"{city}{address}",
                "city": city,
            })
            if retry.get("lat"):
                return retry

        return result

    # 第一次失败，用"城市+地址"重试
    if city:
        retry = await tool_executor.execute("geocode", {
            "address": f"{city}{address}",
            "city": city,
        })
        if retry.get("lat"):
            return retry

    return None


def _get_clarification_chips(ctx: ConversationContext) -> list[str]:
    """根据上下文生成追问快捷词"""
    if ctx.current_city:
        return [f"从{ctx.current_city}站出发", "从当前位置出发", "查看附近站点"]
    return ["查看路线", "附近地铁", "帮助"]
