"""
节点: 通用对话

处理非路线规划的消息，使用 LLM 进行对话。
支持附近地点查询（注入位置上下文）。
"""

import json
import re
import logging
from typing import AsyncGenerator

from app.agent.state import AgentState
from app.agent.prompts.intent import GENERAL_CHAT_SYSTEM
from app.gateway.llm_gateway import llm_gateway
from app.gateway.schemas import LlmRequest
from app.clients.java_api import java_client

logger = logging.getLogger("tmap-python.agent.general_chat")

# 附近关键词
NEARBY_KEYWORDS = [
    "附近", "周边", "旁边", "周围", "就近", "近的", "最近的",
    "当前位置", "我在哪", "我在哪里", "这里是哪", "这是哪",
    "什么位置", "定位", "我的位置", "现在在哪", "在哪", "哪里",
]


async def general_chat_node(state: AgentState) -> dict:
    """
    通用对话节点。

    1. 检测是否是附近查询 → 注入位置上下文
    2. 构建对话历史
    3. 调用 LLM 生成回复
    """
    user_message = state.get("user_message", "")
    chat_history = state.get("chat_history", [])
    lat = state.get("lat", 0)
    lng = state.get("lng", 0)

    # 构建系统提示
    system_prompt = GENERAL_CHAT_SYSTEM

    # 附近查询处理
    is_nearby = any(kw in user_message for kw in NEARBY_KEYWORDS)
    if is_nearby and lat != 0 and lng != 0:
        location_context = await _build_location_context(lat, lng, user_message)
        if location_context:
            system_prompt += f"\n\n【用户位置信息】\n{location_context}"

    # 构建消息列表
    messages = [{"role": "system", "content": system_prompt}]

    # 添加历史消息（最近 N 条）
    max_history = 10
    for msg in chat_history[-max_history:]:
        role = msg.get("role", "user")
        content = msg.get("content", "")
        if content and role in ("user", "assistant"):
            messages.append({"role": role, "content": content})

    # 添加当前用户消息
    messages.append({"role": "user", "content": user_message})

    # 调用 LLM（流式）
    full_reply = ""
    tokens_in = 0
    tokens_out = 0

    try:
        async for chunk in llm_gateway.complete_stream(LlmRequest(
            messages=messages,
            temperature=0.7,
            max_tokens=1024,
        )):
            if chunk.delta:
                full_reply += chunk.delta
            if chunk.usage:
                tokens_in = chunk.usage.input_tokens
                tokens_out = chunk.usage.output_tokens

    except Exception as e:
        logger.error(f"LLM 通用对话失败: {e}")
        full_reply = "抱歉，服务暂时繁忙，请稍后再试。"

    # 提取快捷词
    chips = _extract_chips(full_reply)
    # 从回复中移除快捷词部分
    reply_text = _remove_chips(full_reply)

    return {
        "reply": reply_text,
        "chips": chips,
        "tokens_in": tokens_in,
        "tokens_out": tokens_out,
    }


async def _build_location_context(lat: float, lng: float, user_message: str) -> str:
    """构建位置上下文"""
    context_parts = []

    # 获取地址
    try:
        loc = await java_client.regeo(lng, lat)
        if loc and loc.get("address"):
            context_parts.append(f"用户当前位置: {loc['address']}")
    except Exception:
        pass

    # 提取搜索关键词
    keyword = _extract_nearby_keyword(user_message)
    if keyword:
        try:
            # 尝试 POI 搜索
            results = await java_client.poi_search(keyword, "", lat, lng)
            if results:
                pois = []
                for r in results[:5]:
                    pois.append(f"- {r.get('name', '')} ({r.get('address', '')})")
                context_parts.append(f"附近{keyword}:\n" + "\n".join(pois))
        except Exception:
            pass

    return "\n".join(context_parts) if context_parts else ""


def _extract_nearby_keyword(message: str) -> str:
    """从消息中提取搜索关键词"""
    keyword_map = {
        "美食": ["美食", "吃饭", "餐厅", "小吃", "饭店", "吃"],
        "景点": ["景点", "旅游", "玩", "公园", "景区"],
        "酒店": ["酒店", "宾馆", "住宿", "旅馆"],
        "购物": ["购物", "商场", "超市", "便利店"],
        "医院": ["医院", "诊所", "药店"],
        "地铁站": ["地铁站", "地铁", "站"],
    }
    for keyword, triggers in keyword_map.items():
        for trigger in triggers:
            if trigger in message:
                return keyword
    return "生活服务"


def _extract_chips(reply: str) -> list[str]:
    """从回复末尾提取快捷词"""
    # 匹配末尾的 JSON 数组
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
    # 移除末尾的 JSON 数组
    cleaned = re.sub(r"\n?\[.*?\]$", "", reply, flags=re.DOTALL).strip()
    return cleaned if cleaned else reply
