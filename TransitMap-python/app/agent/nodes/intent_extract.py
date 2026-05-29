"""
节点 2: 意图提取

使用 LLM 从用户消息中提取出行意图（from, to, city）。
"""

import json
import re
import logging
from app.agent.state import AgentState
from app.agent.prompts.intent import INTENT_EXTRACT_PROMPT
from app.gateway.llm_gateway import llm_gateway
from app.gateway.schemas import LlmRequest

logger = logging.getLogger("tmap-python.agent.intent_extract")


async def intent_extract_node(state: AgentState) -> dict:
    """
    意图提取节点。

    使用 LLM 从用户消息中提取 {from, to, city}。
    失败时回退到正则提取。
    """
    user_message = state.get("user_message", "")
    chat_history = state.get("chat_history", [])

    # 构建最近对话上下文
    recent = []
    for msg in chat_history[-5:]:
        role = msg.get("role", "user")
        content = msg.get("content", "")
        if content:
            recent.append(f"{role}: {content}")
    recent_text = "\n".join(recent) if recent else "（无历史）"

    prompt = INTENT_EXTRACT_PROMPT.format(
        user_message=user_message,
        recent_messages=recent_text,
    )

    try:
        reply = await llm_gateway.complete(LlmRequest(
            messages=[{"role": "user", "content": prompt}],
            system_prompt="你是地铁出行意图识别系统，只输出 JSON。",
            temperature=0.1,
            max_tokens=500,
            json_mode=True,
        ))

        result = _parse_json(reply.content)
        if result:
            slots = result.get("slots", {})
            logger.info(f"LLM 提取: intent={result.get('intent')}, slots={slots}")
            return {
                "slot_from": slots.get("from") or None,
                "slot_to": slots.get("to") or None,
                "llm_inferred_city": slots.get("city") or None,
                "tokens_in": reply.usage.input_tokens,
                "tokens_out": reply.usage.output_tokens,
            }

    except Exception as e:
        logger.error(f"LLM 意图提取失败: {e}")

    # 回退：正则提取
    return _regex_extract(user_message)


def _parse_json(content: str) -> dict | None:
    """解析 JSON"""
    if not content:
        return None
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        match = re.search(r"```(?:json)?\s*\n?(.*?)\n?```", content, re.DOTALL)
        if match:
            try:
                return json.loads(match.group(1))
            except json.JSONDecodeError:
                pass
    return None


def _regex_extract(message: str) -> dict:
    """正则回退提取"""
    slot_from = None
    slot_to = None

    # "从A到B" / "从A去B"
    m = re.search(r"从(.+?)(?:到|去)(.+)", message)
    if m:
        slot_from = m.group(1).strip()
        slot_to = m.group(2).strip()
    else:
        # "去B怎么走"
        m = re.search(r"去(.+?)(?:怎么走|怎么去|如何到达)", message)
        if m:
            slot_to = m.group(1).strip()

    logger.info(f"正则提取: from={slot_from}, to={slot_to}")
    return {
        "slot_from": slot_from,
        "slot_to": slot_to,
        "llm_inferred_city": None,
    }
