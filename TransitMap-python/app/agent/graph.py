"""
Agent LangGraph 状态图定义

将 Java 的 5 节点流水线迁移到 Python LangGraph：
classify → intent_extract → resolve_geo → match_city → plan_route → generate_reply
     ↓
  general_chat (直接对话)
     ↓
  handle_order (下单)
"""

import logging
from langgraph.graph import StateGraph, END

from app.agent.state import AgentState
from app.agent.nodes.classify import classify_node
from app.agent.nodes.intent_extract import intent_extract_node
from app.agent.nodes.resolve_geo import resolve_geo_node
from app.agent.nodes.match_city import match_city_node
from app.agent.nodes.plan_route import plan_route_node
from app.agent.nodes.general_chat import general_chat_node
from app.agent.nodes.handle_order import handle_order_node
from app.agent.nodes.generate_reply import generate_reply_node

logger = logging.getLogger("tmap-python.agent.graph")


def build_agent_graph() -> StateGraph:
    """
    构建 Agent 状态图。

    流程:
        classify
           ├─ route → intent_extract → resolve_geo → match_city
           │            ├─ SAME_CITY → plan_route → generate_reply → END
           │            ├─ CROSS_CITY/NO_METRO/MISSING_DEST → generate_reply → END
           │            └─ SAME_STATION/NO_ROUTE → generate_reply → END
           ├─ chat → general_chat → END
           └─ order → handle_order → END
    """
    graph = StateGraph(AgentState)

    # 添加所有节点
    graph.add_node("classify", classify_node)
    graph.add_node("intent_extract", intent_extract_node)
    graph.add_node("resolve_geo", resolve_geo_node)
    graph.add_node("match_city", match_city_node)
    graph.add_node("plan_route", plan_route_node)
    graph.add_node("general_chat", general_chat_node)
    graph.add_node("handle_order", handle_order_node)
    graph.add_node("generate_reply", generate_reply_node)

    # 入口节点
    graph.set_entry_point("classify")

    # classify → 条件路由
    graph.add_conditional_edges(
        "classify",
        _route_by_intent,
        {
            "route": "intent_extract",
            "chat": "general_chat",
            "order": "handle_order",
        },
    )

    # 路线流水线
    graph.add_edge("intent_extract", "resolve_geo")
    graph.add_edge("resolve_geo", "match_city")

    # match_city → 条件路由
    graph.add_conditional_edges(
        "match_city",
        _route_by_scenario,
        {
            "SAME_CITY": "plan_route",
            "CROSS_CITY": "generate_reply",
            "NO_METRO": "generate_reply",
            "MISSING_DEST": "generate_reply",
            "SAME_STATION": "generate_reply",
            "NO_ROUTE": "generate_reply",
        },
    )

    graph.add_edge("plan_route", "generate_reply")

    # 终点
    graph.add_edge("generate_reply", END)
    graph.add_edge("general_chat", END)
    graph.add_edge("handle_order", END)

    return graph.compile()


def _route_by_intent(state: AgentState) -> str:
    """根据意图类型路由"""
    return state.get("intent", "chat")


def _route_by_scenario(state: AgentState) -> str:
    """根据场景路由"""
    scenario = state.get("scenario", "NO_ROUTE")
    if scenario in ("CROSS_CITY", "NO_METRO", "MISSING_DEST", "SAME_STATION", "NO_ROUTE"):
        return scenario
    return "SAME_CITY"
