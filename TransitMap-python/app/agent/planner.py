"""
Agent LLM 规划器

让 LLM 理解用户意图并制定执行计划。
替代硬编码的 if-else 流程。
"""

import json
import re
import logging
from app.gateway.llm_gateway import llm_gateway
from app.gateway.schemas import LlmRequest
from app.agent.tools import TOOLS_DESCRIPTION

logger = logging.getLogger("tmap-python.agent.planner")

PLANNER_PROMPT = """你是城市地铁出行助手的规划器。分析用户意图，提取关键信息。

## 当前上下文
{context}

## 用户消息
{user_message}

## 对话历史（最近几条）
{recent_messages}

## 任务
分析用户意图，返回严格的 JSON 格式（只输出 JSON，不要输出其他内容）：

```json
{{
  "intent": "route|chat|order|clarification",
  "city": "城市名（从上下文或消息推断）",
  "from": "出发地（仅路线查询）",
  "to": "目的地（仅路线查询）",
  "clarification": "需要追问的问题（仅 clarification 时）",
  "confidence": 0.9
}}
```

## 规则
1. 如果用户在问路线（"从A到B"、"怎么去"），intent=route，提取 from 和 to
2. 如果用户想下单（"下单"、"买票"），intent=order
3. 如果缺少关键信息（没有出发地或目的地），intent=clarification
4. 其他问题，intent=chat
5. 城市优先从上下文获取，其次从消息推断
6. 如果用户说"去XX怎么走"，from 为空，to 为 "XX"
7. 如果用户说"从XX到YY"，from 为 "XX"，to 为 "YY"
6. 城市优先从上下文获取，其次从消息推断
7. 如果缺少关键信息（如出发地），用 clarification 追问
8. geocode 时一定要带 city 参数，避免定位到错误城市
9. 如果上下文有城市，geocode 的 city 参数用上下文的城市"""


class PlannerResult:
    """规划结果"""
    def __init__(self, data: dict):
        self.raw = data
        self.intent = data.get("intent", "chat")
        self.city = data.get("city", "")
        self.from_location = data.get("from", "")
        self.to_location = data.get("to", "")
        self.clarification = data.get("clarification", "")
        self.confidence = data.get("confidence", 0.5)


async def plan(
    user_message: str,
    context: dict,
    chat_history: list[dict],
) -> PlannerResult:
    """
    调用 LLM 制定执行计划。

    Args:
        user_message: 用户消息
        context: 上下文信息
        chat_history: 对话历史

    Returns:
        PlannerResult 执行计划
    """
    # 构建最近对话摘要
    recent = []
    for msg in chat_history[-5:]:
        role = msg.get("role", "user")
        content = msg.get("content", "")
        if content:
            recent.append(f"{role}: {content[:200]}")
    recent_text = "\n".join(recent) if recent else "（无历史）"

    # 构建上下文摘要
    context_text = json.dumps(context, ensure_ascii=False, indent=2)

    prompt = PLANNER_PROMPT.format(
        context=context_text,
        tools=TOOLS_DESCRIPTION,
        user_message=user_message,
        recent_messages=recent_text,
    )

    try:
        reply = await llm_gateway.complete(LlmRequest(
            messages=[{"role": "user", "content": prompt}],
            system_prompt="你是地铁出行助手的规划器，只输出 JSON。",
            temperature=0.1,
            max_tokens=1000,
            json_mode=True,
        ))

        data = _parse_json(reply.content)
        if data:
            result = PlannerResult(data)
            logger.info(
                f"规划完成: intent={result.intent}, city={result.city}, "
                f"from={result.from_location}, to={result.to_location}"
            )
            return result

    except Exception as e:
        logger.error(f"规划器调用失败: {e}")

    # 降级：简单规则
    return _fallback_plan(user_message, context)


def _fallback_plan(user_message: str, context: dict) -> PlannerResult:
    """降级规划（LLM 失败时）"""
    import re

    # 路线模式
    route_match = re.search(r"从(.+?)(?:到|去)(.+)", user_message)
    if route_match:
        return PlannerResult({
            "intent": "route",
            "city": context.get("current_city", ""),
            "from": route_match.group(1).strip(),
            "to": route_match.group(2).strip(),
            "confidence": 0.6,
        })

    # 默认：聊天
    return PlannerResult({
        "intent": "chat",
        "city": context.get("current_city", ""),
        "confidence": 0.5,
    })


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
