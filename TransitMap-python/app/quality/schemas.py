"""
对话质量评估 — 数据模型
"""

from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class QualityScore(BaseModel):
    """质量评分"""
    message_id: int
    session_id: int
    user_id: Optional[int] = None
    quality_score: float = 0.0        # 0.00-1.00
    user_feedback: Optional[str] = None  # positive/negative
    feedback_detail: str = ""
    response_time_ms: int = 0
    token_count: int = 0
    intent_type: str = ""             # route/chat/order
    scenario: str = ""
    is_hallucination: bool = False
    is_off_topic: bool = False


class QualityStats(BaseModel):
    """质量统计"""
    total_conversations: int = 0
    positive_count: int = 0
    negative_count: int = 0
    positive_rate: float = 0.0
    avg_response_time_ms: int = 0
    avg_quality_score: float = 0.0
    hallucination_count: int = 0
    hallucination_rate: float = 0.0
    by_intent: dict = {}              # {"route": {"count": N, "avg_score": X}, ...}


class FeedbackRequest(BaseModel):
    """用户反馈请求"""
    message_id: int
    feedback: str                     # positive/negative
    detail: str = ""
    user_id: Optional[int] = None
    session_id: Optional[int] = None


class LowScoreConversation(BaseModel):
    """低分对话记录"""
    id: int
    message_id: int
    session_id: int
    user_id: Optional[int] = None
    quality_score: float = 0.0
    user_feedback: Optional[str] = None
    intent_type: str = ""
    scenario: str = ""
    is_hallucination: bool = False
    is_off_topic: bool = False
    created_at: Optional[datetime] = None
    # 关联消息内容（从 chat_message 表获取）
    user_message: str = ""
    assistant_reply: str = ""
