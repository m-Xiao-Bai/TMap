"""
Agent 智能回复生成器

根据执行结果生成自然语言回复。
支持路线卡片、通用对话、追问等场景。
"""

import json
import re
import logging
from app.gateway.llm_gateway import llm_gateway
from app.gateway.schemas import LlmRequest

logger = logging.getLogger("tmap-python.agent.responder")

ROUTE_REPLY_PROMPT = """你是城市地铁出行助手。根据路线规划结果，生成友好的回复。

## 用户问题
{user_message}

## 路线信息
{route_info}

## 回复规则
1. 用简洁友好的语言描述路线
2. 列出关键信息：途经站点数、换乘次数、预计时间、票价
3. 用 🚇 emoji 增加趣味（最多1个）
4. 不要输出 JSON、代码块
5. 不要编造数据，所有信息以路线数据为准
6. 回复末尾返回 3 个快捷词，格式：["词1","词2","词3"]"""

CHAT_REPLY_PROMPT = """你是城市出行与生活助手「TMap」。

## 用户问题
{user_message}

## 上下文
{context}

## 回复规则
1. 严格基于事实回答，不要编造数据
2. 如果不确定，坦诚说明
3. 回答简洁清晰
4. 用 🚇 emoji 增加趣味（最多1个）
5. 如果涉及路线规划，引导用户说"从哪到哪"
6. 回复末尾返回 3 个快捷词，格式：["词1","词2","词3"]"""


async def generate_route_reply(
    user_message: str,
    route_plan: dict,
    from_station: dict,
    to_station: dict,
) -> dict:
    """
    生成路线回复。

    Returns:
        {"reply": str, "chips": list[str], "cards": list[dict]}
    """
    # 构建路线摘要
    stations = route_plan.get("stations", [])
    transfers = route_plan.get("transfers", [])
    price = route_plan.get("price", "?")
    duration = route_plan.get("durationMinutes", "?")

    route_info = f"""
出发站: {from_station.get('stationName', '未知')}
到达站: {to_station.get('stationName', '未知')}
途经站点: {len(stations)} 站
站点列表: {' → '.join(s.get('stationName', '') for s in stations)}
换乘次数: {len(transfers)} 次
票价: {price} 元
预计时间: {duration} 分钟"""

    prompt = ROUTE_REPLY_PROMPT.format(
        user_message=user_message,
        route_info=route_info,
    )

    try:
        reply_text = ""
        async for chunk in llm_gateway.complete_stream(LlmRequest(
            messages=[{"role": "user", "content": prompt}],
            system_prompt="你是地铁出行助手，只输出自然语言。",
            temperature=0.5,
            max_tokens=500,
        )):
            if chunk.delta:
                reply_text += chunk.delta

        chips = _extract_chips(reply_text)
        reply_text = _remove_chips(reply_text)

        return {
            "reply": reply_text,
            "chips": chips,
            "cards": [{"kind": "ROUTE_CARD", "payload": route_plan}],
        }

    except Exception as e:
        logger.error(f"路线回复生成失败: {e}")
        return _fallback_route_reply(route_plan, from_station, to_station)


async def generate_chat_reply(
    user_message: str,
    context: dict,
    chat_history: list[dict],
) -> dict:
    """
    生成通用对话回复。

    Returns:
        {"reply": str, "chips": list[str]}
    """
    # 构建上下文
    context_text = ""
    if context.get("current_city"):
        context_text += f"当前城市: {context['current_city']}\n"
    if context.get("recent_from"):
        context_text += f"最近出发地: {context['recent_from']}\n"
    if context.get("recent_to"):
        context_text += f"最近目的地: {context['recent_to']}\n"

    # 构建对话历史
    messages = [{"role": "system", "content": CHAT_REPLY_PROMPT.format(
        user_message=user_message,
        context=context_text or "（无上下文）",
    )}]

    for msg in chat_history[-5:]:
        role = msg.get("role", "user")
        content = msg.get("content", "")
        if content and role in ("user", "assistant"):
            messages.append({"role": role, "content": content[:300]})

    messages.append({"role": "user", "content": user_message})

    try:
        reply_text = ""
        async for chunk in llm_gateway.complete_stream(LlmRequest(
            messages=messages,
            temperature=0.7,
            max_tokens=500,
        )):
            if chunk.delta:
                reply_text += chunk.delta

        chips = _extract_chips(reply_text)
        reply_text = _remove_chips(reply_text)

        return {"reply": reply_text, "chips": chips}

    except Exception as e:
        logger.error(f"对话回复生成失败: {e}")
        return {"reply": "抱歉，服务暂时繁忙，请稍后再试。", "chips": ["重试", "帮助"]}


def _fallback_route_reply(route_plan: dict, from_station: dict, to_station: dict) -> dict:
    """降级路线回复"""
    stations = route_plan.get("stations", [])
    price = route_plan.get("price", "?")
    duration = route_plan.get("durationMinutes", "?")

    reply = f"路线规划完成 🚇\n\n"
    reply += f"从 **{from_station.get('stationName', '出发站')}** 到 **{to_station.get('stationName', '到达站')}**\n\n"
    reply += f"途经 {len(stations)} 站，票价约 {price} 元，预计 {duration} 分钟"

    return {
        "reply": reply,
        "chips": ["查看换乘", "附近站点", "重新规划"],
        "cards": [{"kind": "ROUTE_CARD", "payload": route_plan}],
    }


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
    """从回复中移除快捷词"""
    cleaned = re.sub(r"\n?\[.*?\]$", "", reply, flags=re.DOTALL).strip()
    return cleaned if cleaned else reply
