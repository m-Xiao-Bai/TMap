"""
节点 1: 消息分类

使用正则表达式对用户消息进行快速分类。
规则优先于 LLM，确保确定性行为。
"""

import re
import logging
from app.agent.state import AgentState

logger = logging.getLogger("tmap-python.agent.classify")

# 路线规划模式
ROUTE_PATTERNS = [
    r"从.+到.+",
    r"从.+去.+",
    r"去.+怎么走",
    r"去.+怎么去",
    r"去.+怎么到达",
    r"去.+如何到达",
    r".+到.+怎么走",
    r".+到.+怎么去",
    r".+到.+路线",
    r"怎么去.+",
    r"怎么从.+到.+",
    r"如何从.+到.+",
    r".+地铁站到.+地铁站",
    r"坐地铁从.+到.+",
    r"帮我规划.+路线",
    r"帮我查.+路线",
]
ROUTE_RE = re.compile("|".join(ROUTE_PATTERNS))

# 下单模式
ORDER_KEYWORDS = ["下单", "买票", "确认", "购买", "订票", "ok", "好的", "确认下单"]
ORDER_RE = re.compile("|".join(re.escape(kw) for kw in ORDER_KEYWORDS))


async def classify_node(state: AgentState) -> dict:
    """
    消息分类节点。

    分类逻辑：
    1. 检查是否是下单请求（关键词匹配）
    2. 检查是否是路线规划请求（正则匹配）
    3. 其他 → 通用对话
    """
    message = state.get("user_message", "").strip()
    if not message:
        return {"intent": "chat"}

    # 检查下单（需要上下文中有路线卡片）
    chat_history = state.get("chat_history", [])
    has_recent_route = any(
        msg.get("extras", {}).get("kind") == "ROUTE_CARD"
        for msg in chat_history[-3:]
    )
    if has_recent_route and ORDER_RE.search(message):
        logger.info(f"分类: order (匹配下单关键词)")
        return {"intent": "order"}

    # 检查路线规划
    if ROUTE_RE.search(message):
        logger.info(f"分类: route (匹配路线模式)")
        return {"intent": "route"}

    # 默认：通用对话
    logger.info(f"分类: chat (默认)")
    return {"intent": "chat"}
