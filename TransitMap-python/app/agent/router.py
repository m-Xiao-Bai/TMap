"""
Agent 服务 — API 路由

使用智能 Agent 处理对话，SSE 流式输出。
"""

import json
import logging
from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.dependencies import verify_api_key

logger = logging.getLogger("tmap-python.agent")

router = APIRouter()


class AgentChatRequest(BaseModel):
    """Agent 对话请求"""
    user_message: str
    session_id: str = ""
    user_id: int | None = None
    lat: float = 0.0
    lng: float = 0.0
    chat_history: list[dict] = []


@router.post("/chat")
async def agent_chat(
    request: AgentChatRequest,
    api_key: str = Depends(verify_api_key),
):
    """
    Agent 对话（SSE 流式）。

    使用智能 Agent 处理：
    1. 上下文管理
    2. LLM 规划
    3. 工具执行
    4. 智能回复
    """
    from app.agent.smart_agent import process_message

    async def event_stream():
        try:
            yield _sse({"type": "status", "text": "正在思考..."})

            result = await process_message(request.model_dump())

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
