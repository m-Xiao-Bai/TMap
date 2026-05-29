"""
LLM 供应商 — 注册表

根据配置动态创建供应商实例。
"""

import logging
from app.config import settings, LlmProviderConfig
from app.gateway.providers.base import BaseLlmProvider
from app.gateway.providers.openai_compat import OpenAICompatProvider

logger = logging.getLogger("tmap-python.llm")

# 供应商类型注册表
PROVIDER_TYPES = {
    "deepseek": OpenAICompatProvider,
    "qwen": OpenAICompatProvider,
    "kimi": OpenAICompatProvider,
    "doubao": OpenAICompatProvider,
    "zhipu": OpenAICompatProvider,
    "siliconflow": OpenAICompatProvider,
    "openai": OpenAICompatProvider,
    "openai-compatible": OpenAICompatProvider,
}


class ProviderRegistry:
    """供应商注册表，管理所有已注册的 LLM 供应商实例"""

    def __init__(self):
        self._providers: dict[str, BaseLlmProvider] = {}
        self._default_name: str = ""

    def register_all(self):
        """从配置加载并注册所有供应商"""
        for cfg in settings.llm_providers:
            self.register(cfg)
        logger.info(f"已注册 {len(self._providers)} 个 LLM 供应商")

    def register(self, cfg: LlmProviderConfig):
        """注册单个供应商"""
        provider_type = PROVIDER_TYPES.get(cfg.name.lower())
        if not provider_type:
            # 默认使用 OpenAI-compatible
            provider_type = OpenAICompatProvider
            logger.warning(f"未知供应商类型 '{cfg.name}'，使用 OpenAI-compatible 适配器")

        provider = provider_type(
            name=cfg.name,
            base_url=cfg.base_url,
            api_key=cfg.api_key,
            model=cfg.model,
            timeout_ms=cfg.timeout_ms,
            max_tokens=cfg.max_tokens,
            temperature=cfg.temperature,
        )
        self._providers[cfg.name] = provider

        if cfg.is_default:
            self._default_name = cfg.name

        logger.info(f"注册供应商: {cfg.name} ({cfg.model})")

    def get(self, name: str = "") -> BaseLlmProvider | None:
        """获取供应商实例"""
        if name:
            return self._providers.get(name)
        # 返回默认供应商
        if self._default_name:
            return self._providers.get(self._default_name)
        # 返回第一个
        if self._providers:
            return next(iter(self._providers.values()))
        return None

    def get_default(self) -> BaseLlmProvider | None:
        """获取默认供应商"""
        return self.get()

    @property
    def providers(self) -> dict[str, BaseLlmProvider]:
        return self._providers

    async def close_all(self):
        """关闭所有供应商连接"""
        for provider in self._providers.values():
            if hasattr(provider, "close"):
                await provider.close()


# 全局单例
provider_registry = ProviderRegistry()
