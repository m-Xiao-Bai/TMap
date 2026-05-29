"""
Agent 服务 — API 路由

使用 LangGraph 状态图处理对话，SSE 流式输出。
"""

import json
import logging
from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.dependencies import verify_api_key
from app.agent.state import AgentState

logger = logging.getLogger("tmap-python.agent")

router = APIRouter()

# LangGraph 图实例（延迟初始化）
_agent_graph = None


def _get_graph():
    """延迟初始化 LangGraph 图"""
    global _agent_graph
    if _agent_graph is None:
        from app.agent.graph import build_agent_graph
        _agent_graph = build_agent_graph()
        logger.info("LangGraph Agent 图已初始化")
    return _agent_graph


# ── 请求/响应模型 ──

class AgentChatRequest(BaseModel):
    """Agent 对话请求"""
    user_message: str
    session_id: str = ""
    user_id: int | None = None
    lat: float = 0.0
    lng: float = 0.0
    chat_history: list[dict] = []


# ── API 端点 ──

@router.post("/chat")
async def agent_chat(
    request: AgentChatRequest,
    api_key: str = Depends(verify_api_key),
):
    """
    Agent 对话（SSE 流式）。

    使用 LangGraph 状态图处理完整流程：
    classify → intent_extract → resolve_geo → match_city → plan_route → generate_reply
    """
    graph = _get_graph()

    # 构建初始状态
    initial_state: AgentState = {
        "user_message": request.user_message,
        "session_id": request.session_id,
        "user_id": request.user_id,
        "lat": request.lat,
        "lng": request.lng,
        "chat_history": request.chat_history,
        # 默认值
        "intent": "chat",
        "slot_from": None,
        "slot_to": None,
        "llm_inferred_city": None,
        "from_geo": None,
        "to_geo": None,
        "from_city_id": None,
        "to_city_id": None,
        "scenario": None,
        "from_station": None,
        "to_station": None,
        "route_plan": None,
        "reply": "",
        "cards": [],
        "chips": [],
        "short_circuit": False,
        "tokens_in": 0,
        "tokens_out": 0,
    }

    async def event_stream():
        """SSE 事件流"""
        try:
            # 发送状态：正在思考
            yield _sse({"type": "status", "text": "正在理解你的需求..."})

            # 执行 LangGraph 图
            result = await graph.ainvoke(initial_state)

            # 发送回复
            reply = result.get("reply", "")
            if reply:
                yield _sse({"type": "delta", "text": reply})

            # 发送卡片
            for card in result.get("cards", []):
                yield _sse({"type": "card", "data": card})

            # 发送快捷词
            chips = result.get("chips", [])
            if chips:
                yield _sse({"type": "chips", "items": chips})

            # 发送完成
            yield _sse({
                "type": "done",
                "tokensIn": result.get("tokens_in", 0),
                "tokensOut": result.get("tokens_out", 0),
            })

        except Exception as e:
            logger.error(f"Agent 处理异常: {e}", exc_info=True)
            yield _sse({
                "type": "error",
                "code": "AGENT_ERROR",
                "message": "处理请求时出错，请稍后再试",
            })

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


def _sse(data: dict) -> str:
    """格式化 SSE 事件"""
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.get("/stats")
async def agent_stats(
    api_key: str = Depends(verify_api_key),
):
    """Agent 引擎统计"""
    from app.gateway.token_tracker import token_tracker
    total = await token_tracker.get_total()
    return {
        "code": 200,
        "data": {
            "total_calls": total.total_calls,
            "total_input_tokens": total.total_input_tokens,
            "total_output_tokens": total.total_output_tokens,
        },
    }
