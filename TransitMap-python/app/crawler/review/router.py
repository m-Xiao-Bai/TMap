"""
审核服务 — API 路由

Phase 2 将实现完整的审核逻辑。
当前为接口定义。
"""

import logging
from fastapi import APIRouter, Depends

from app.dependencies import verify_api_key
from app.crawler.schemas import ReviewActionRequest, BatchReviewRequest

logger = logging.getLogger("tmap-python.review")

router = APIRouter()


@router.get("/pending")
async def get_pending_reviews(
    city: str = "",
    page: int = 1,
    size: int = 20,
    api_key: str = Depends(verify_api_key),
):
    """获取待审核列表"""
    # Phase 2 实现
    return {
        "code": 200,
        "data": {
            "items": [],
            "total": 0,
            "page": page,
            "size": size,
        },
    }


@router.post("/approve/{review_id}")
async def approve_review(
    review_id: int,
    api_key: str = Depends(verify_api_key),
):
    """批准单条审核"""
    # Phase 2 实现
    return {"code": 200, "message": "已批准"}


@router.post("/reject/{review_id}")
async def reject_review(
    review_id: int,
    api_key: str = Depends(verify_api_key),
):
    """拒绝单条审核"""
    # Phase 2 实现
    return {"code": 200, "message": "已拒绝"}


@router.post("/batch")
async def batch_review(
    request: BatchReviewRequest,
    api_key: str = Depends(verify_api_key),
):
    """批量审核"""
    # Phase 2 实现
    return {
        "code": 200,
        "message": f"已处理 {len(request.review_ids)} 条",
    }
