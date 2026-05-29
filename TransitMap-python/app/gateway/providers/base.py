"""
LLM 供应商 — 抽象基类
"""

from abc import ABC, abstractmethod
from typing import AsyncGenerator
from app.gateway.schemas import LlmRequest, LlmReply, LlmStreamChunk


class BaseLlmProvider(ABC):
    """LLM 供应商抽象接口"""

    def __init__(self, name: str, base_url: str, api_key: str, model: str, **kwargs):
        self.name = name
        self.base_url = base_url
        self.api_key = api_key
        self.model = model
        self.timeout_ms = kwargs.get("timeout_ms", 30000)
        self.max_tokens = kwargs.get("max_tokens", 1024)
        self.temperature = kwargs.get("temperature", 0.3)

    @abstractmethod
    async def complete(self, request: LlmRequest) -> LlmReply:
        """同步调用"""
        ...

    @abstractmethod
    async def complete_stream(self, request: LlmRequest) -> AsyncGenerator[LlmStreamChunk, None]:
        """流式调用"""
        ...

    async def ping(self) -> bool:
        """健康检查"""
        try:
            reply = await self.complete(LlmRequest(
                messages=[{"role": "user", "content": "ping"}],
                max_tokens=5,
                timeout_ms=5000,
            ))
            return bool(reply.content)
        except Exception:
            return False

    def __repr__(self):
        return f"<{self.__class__.__name__} name={self.name} model={self.model}>"
