"""
对话质量评估 — API 路由
"""

import logging
from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.dependencies import verify_api_key
from app.quality.evaluator import quality_evaluator
from app.quality.schemas import FeedbackRequest

logger = logging.getLogger("tmap-python.quality")

router = APIRouter()


class EvaluateRequest(BaseModel):
    """评估请求"""
    message_id: int
    session_id: int
    user_id: int | None = None
    user_message: str
    assistant_reply: str
    intent_type: str = "chat"
    scenario: str = ""
    response_time_ms: int = 0
    token_count: int = 0


@router.post("/evaluate")
async def evaluate_quality(
    request: EvaluateRequest,
    api_key: str = Depends(verify_api_key),
):
    """评估单条对话质量"""
    score = await quality_evaluator.evaluate(
        message_id=request.message_id,
        session_id=request.session_id,
        user_id=request.user_id,
        user_message=request.user_message,
        assistant_reply=request.assistant_reply,
        intent_type=request.intent_type,
        scenario=request.scenario,
        response_time_ms=request.response_time_ms,
        token_count=request.token_count,
    )
    return {
        "code": 200,
        "data": {
            "message_id": score.message_id,
            "quality_score": score.quality_score,
            "is_hallucination": score.is_hallucination,
            "is_off_topic": score.is_off_topic,
        },
    }


@router.post("/feedback")
async def submit_feedback(
    request: FeedbackRequest,
    api_key: str = Depends(verify_api_key),
):
    """提交用户反馈"""
    await quality_evaluator.submit_feedback(
        message_id=request.message_id,
        feedback=request.feedback,
        detail=request.detail,
        user_id=request.user_id,
        session_id=request.session_id,
    )
    return {"code": 200, "message": "反馈已记录"}


@router.get("/stats")
async def quality_stats(
    days: int = 7,
    api_key: str = Depends(verify_api_key),
):
    """获取质量统计"""
    stats = await quality_evaluator.get_stats(days=days)
    return {
        "code": 200,
        "data": {
            "total_conversations": stats.total_conversations,
            "positive_count": stats.positive_count,
            "negative_count": stats.negative_count,
            "positive_rate": stats.positive_rate,
            "avg_response_time_ms": stats.avg_response_time_ms,
            "avg_quality_score": stats.avg_quality_score,
            "hallucination_count": stats.hallucination_count,
            "hallucination_rate": stats.hallucination_rate,
            "by_intent": stats.by_intent,
        },
    }


@router.get("/low-score")
async def low_score_conversations(
    page: int = 1,
    size: int = 20,
    api_key: str = Depends(verify_api_key),
):
    """获取低分对话列表"""
    items = await quality_evaluator.get_low_score(page=page, size=size)
    return {
        "code": 200,
        "data": {
            "items": items,
            "total": len(items),
            "page": page,
            "size": size,
        },
    }
