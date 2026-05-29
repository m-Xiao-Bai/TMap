"""
LLM 统一网关

核心功能：
- 多供应商管理
- 流式/同步统一接口
- 自动降级（主供应商失败 → 备用）
- Token 统计
"""

import logging
from typing import AsyncGenerator

from app.gateway.schemas import LlmRequest, LlmReply, LlmStreamChunk, LlmUsage
from app.gateway.providers.base import BaseLlmProvider
from app.gateway.providers.provider_registry import provider_registry
from app.gateway.token_tracker import token_tracker

logger = logging.getLogger("tmap-python.llm")


class LlmGateway:
    """
    统一 LLM 网关。

    所有 LLM 调用都通过此网关进行，自动处理：
    - 供应商选择
    - 失败重试 + 降级
    - Token 统计
    """

    def __init__(self):
        self._initialized = False

    async def init(self):
        """初始化网关，注册所有供应商"""
        provider_registry.register_all()
        self._initialized = True

    async def close(self):
        """关闭网关"""
        await provider_registry.close_all()
        self._initialized = False

    def _get_provider(self, request: LlmRequest) -> BaseLlmProvider:
        """根据请求选择供应商"""
        if request.provider:
            provider = provider_registry.get(request.provider)
            if provider:
                return provider
            logger.warning(f"未找到供应商 '{request.provider}'，使用默认供应商")

        provider = provider_registry.get_default()
        if not provider:
            raise RuntimeError("没有可用的 LLM 供应商，请检查配置")
        return provider

    async def complete(self, request: LlmRequest) -> LlmReply:
        """
        同步调用 LLM。

        自动处理：
        1. 选择供应商
        2. 调用失败时尝试备用供应商
        3. 记录 Token 用量
        """
        provider = self._get_provider(request)

        try:
            reply = await provider.complete(request)
        except Exception as e:
            logger.error(f"供应商 {provider.name} 调用失败: {e}")
            # 尝试降级到其他供应商
            reply = await self._fallback_complete(request, exclude=provider.name)

        # 记录用量
        await self._track_usage(request, reply)
        return reply

    async def complete_stream(
        self, request: LlmRequest
    ) -> AsyncGenerator[LlmStreamChunk, None]:
        """
        流式调用 LLM。

        yield 每个 chunk，最后一个 chunk 包含 usage。
        """
        provider = self._get_provider(request)

        try:
            total_usage = LlmUsage()
            async for chunk in provider.complete_stream(request):
                if chunk.usage:
                    total_usage = chunk.usage
                yield chunk

            # 记录用量
            if total_usage.input_tokens > 0 or total_usage.output_tokens > 0:
                reply = LlmReply(content="", usage=total_usage, provider=provider.name)
                await self._track_usage(request, reply)

        except Exception as e:
            logger.error(f"供应商 {provider.name} 流式调用失败: {e}")
            # 流式调用降级：回退到同步调用
            yield LlmStreamChunk(delta=f"[服务暂时不可用: {provider.name}]", finish_reason="stop")

    async def complete_with_fallback(self, request: LlmRequest) -> LlmReply:
        """
        带自动降级的同步调用。

        先尝试指定供应商，失败后依次尝试其他供应商。
        """
        provider = self._get_provider(request)

        try:
            reply = await provider.complete(request)
            await self._track_usage(request, reply)
            return reply
        except Exception as e:
            logger.warning(f"供应商 {provider.name} 失败，尝试降级: {e}")
            return await self._fallback_complete(request, exclude=provider.name)

    async def _fallback_complete(
        self, request: LlmRequest, exclude: str
    ) -> LlmReply:
        """降级调用：依次尝试其他供应商"""
        for name, provider in provider_registry.providers.items():
            if name == exclude:
                continue
            try:
                logger.info(f"降级到供应商: {name}")
                reply = await provider.complete(request)
                await self._track_usage(request, reply)
                return reply
            except Exception as e:
                logger.warning(f"降级供应商 {name} 也失败: {e}")
                continue

        raise RuntimeError("所有 LLM 供应商均不可用")

    async def _track_usage(self, request: LlmRequest, reply: LlmReply):
        """记录 Token 用量"""
        try:
            await token_tracker.track(
                user_id=None,  # 由调用方传入
                session_id="",
                provider=reply.provider,
                model=reply.model,
                usage=reply.usage,
            )
        except Exception as e:
            logger.warning(f"Token 统计记录失败: {e}")

    async def ping(self) -> bool:
        """健康检查：检查默认供应商是否可用"""
        provider = provider_registry.get_default()
        if not provider:
            return False
        return await provider.ping()

    async def ping_all(self) -> dict[str, bool]:
        """检查所有供应商健康状态"""
        results = {}
        for name, provider in provider_registry.providers.items():
            results[name] = await provider.ping()
        return results


# 全局单例
llm_gateway = LlmGateway()
