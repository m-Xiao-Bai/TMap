"""
节点: 回复生成

根据场景生成最终回复。
成功场景使用 LLM 流式生成，失败场景使用模板。
"""

import json
import re
import logging
from app.agent.state import AgentState
from app.agent.prompts.system import (
    ROUTE_SYSTEM_PROMPT,
    CROSS_CITY_PROMPT,
    MISSING_DEST_PROMPT,
)
from app.gateway.llm_gateway import llm_gateway
from app.gateway.schemas import LlmRequest

logger = logging.getLogger("tmap-python.agent.generate_reply")

# 场景模板回复
SCENARIO_TEMPLATES = {
    "CROSS_CITY": CROSS_CITY_PROMPT,
    "NO_METRO": "抱歉，该城市暂未接入地铁数据。你可以请求管理员添加该城市 🚇",
    "MISSING_DEST": MISSING_DEST_PROMPT,
    "SAME_STATION": "出发站和到达站是同一个站，不需要乘坐地铁哦 🚇",
    "NO_ROUTE": "抱歉，未找到合适的路线。请检查出发地和目的地是否正确 🚇",
}


async def generate_reply_node(state: AgentState) -> dict:
    """
    回复生成节点。

    - 失败场景：使用模板回复
    - 成功场景：使用 LLM 流式生成
    """
    scenario = state.get("scenario", "NO_ROUTE")

    # 失败场景 → 模板回复
    if scenario in SCENARIO_TEMPLATES:
        template = SCENARIO_TEMPLATES[scenario]
        chips = _default_chips(scenario)
        return {
            "reply": template,
            "chips": chips,
        }

    # 成功场景 → LLM 生成
    route_plan = state.get("route_plan")
    if not route_plan:
        return {
            "reply": "路线规划数据异常，请重试 🚇",
            "chips": ["重新规划"],
        }

    return await _generate_route_reply(state, route_plan)


async def _generate_route_reply(state: AgentState, route_plan: dict) -> dict:
    """使用 LLM 生成路线回复"""
    user_message = state.get("user_message", "")
    slot_from = state.get("slot_from", "你的位置")
    slot_to = state.get("slot_to", "目的地")
    from_station = state.get("from_station", {})
    to_station = state.get("to_station", {})

    # 构建路线摘要
    route_summary = _build_route_summary(route_plan, from_station, to_station, slot_from, slot_to)

    # 构建系统提示
    system_prompt = ROUTE_SYSTEM_PROMPT
    system_prompt += f"\n\n【当前路线数据】\n{route_summary}"

    # 构建消息
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_message},
    ]

    # LLM 流式生成
    full_reply = ""
    tokens_in = 0
    tokens_out = 0

    try:
        async for chunk in llm_gateway.complete_stream(LlmRequest(
            messages=messages,
            temperature=0.5,
            max_tokens=1024,
        )):
            if chunk.delta:
                full_reply += chunk.delta
            if chunk.usage:
                tokens_in = chunk.usage.input_tokens
                tokens_out = chunk.usage.output_tokens

    except Exception as e:
        logger.error(f"LLM 路线回复生成失败: {e}")
        full_reply = _fallback_route_reply(route_plan, from_station, to_station)

    # 提取快捷词
    chips = _extract_chips(full_reply)
    reply_text = _remove_chips(full_reply)

    # 构建路线卡片
    cards = [{
        "kind": "ROUTE_CARD",
        "payload": route_plan,
    }]

    return {
        "reply": reply_text,
        "cards": cards,
        "chips": chips,
        "tokens_in": tokens_in,
        "tokens_out": tokens_out,
    }


def _build_route_summary(
    route_plan: dict,
    from_station: dict,
    to_station: dict,
    slot_from: str,
    slot_to: str,
) -> str:
    """构建路线摘要（注入 LLM 上下文）"""
    parts = []
    parts.append(f"出发地: {slot_from}")
    parts.append(f"目的地: {slot_to}")
    parts.append(f"出发站: {from_station.get('stationName', '未知')}")
    parts.append(f"到达站: {to_station.get('stationName', '未知')}")

    # 站点列表
    stations = route_plan.get("stations", [])
    if stations:
        station_names = [s.get("stationName", "") for s in stations]
        parts.append(f"途经站点 ({len(stations)} 站): {' → '.join(station_names)}")

    # 换乘信息
    transfers = route_plan.get("transfers", [])
    if transfers:
        parts.append(f"换乘 {len(transfers)} 次")

    # 票价
    price = route_plan.get("price")
    if price:
        parts.append(f"票价: {price} 元")

    # 时间
    duration = route_plan.get("durationMinutes")
    if duration:
        parts.append(f"预计耗时: {duration} 分钟")

    return "\n".join(parts)


def _fallback_route_reply(
    route_plan: dict,
    from_station: dict,
    to_station: dict,
) -> str:
    """LLM 失败时的兜底回复"""
    stations = route_plan.get("stations", [])
    price = route_plan.get("price", "?")
    duration = route_plan.get("durationMinutes", "?")

    reply = f"路线规划完成 🚇\n\n"
    reply += f"从 **{from_station.get('stationName', '出发站')}** 到 **{to_station.get('stationName', '到达站')}**\n\n"

    if stations:
        reply += f"途经 {len(stations)} 站：\n"
        for s in stations:
            reply += f"- {s.get('stationName', '')}\n"

    reply += f"\n票价约 {price} 元，预计 {duration} 分钟"
    return reply


def _default_chips(scenario: str) -> list[str]:
    """根据场景返回默认快捷词"""
    chip_map = {
        "CROSS_CITY": ["查看单城路线", "切换城市"],
        "NO_METRO": ["请求添加城市", "查看已支持城市"],
        "MISSING_DEST": ["我要去...", "附近地铁"],
        "SAME_STATION": ["查看附近站点", "重新规划"],
        "NO_ROUTE": ["重新规划", "查看附近站点"],
    }
    return chip_map.get(scenario, ["查看路线", "重新规划"])


def _extract_chips(reply: str) -> list[str]:
    """从回复末尾提取快捷词"""
    match = re.search(r"\[.*?\]$", reply, re.DOTALL)
    if match:
        try:
            chips = json.loads(match.group())
            if isinstance(chips, list) and all(isinstance(c, str) for c in chips):
                return chips[:3]
        except json.JSONDecodeError:
            pass
    return ["查看路线", "附近站点", "换乘方案"]


def _remove_chips(reply: str) -> str:
    """从回复中移除快捷词部分"""
    cleaned = re.sub(r"\n?\[.*?\]$", "", reply, flags=re.DOTALL).strip()
    return cleaned if cleaned else reply
