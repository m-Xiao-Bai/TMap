"""
LLM 网关测试
"""

import pytest
from app.gateway.schemas import LlmRequest, LlmMessage, LlmUsage


class TestLlmSchemas:
    """LLM 数据模型测试"""

    def test_request_messages_from_dict(self):
        req = LlmRequest(
            messages=[{"role": "user", "content": "hello"}],
        )
        msgs = req.get_messages()
        assert len(msgs) == 1
        assert msgs[0]["role"] == "user"

    def test_request_messages_from_object(self):
        req = LlmRequest(
            messages=[LlmMessage(role="user", content="hello")],
        )
        msgs = req.get_messages()
        assert len(msgs) == 1
        assert msgs[0]["content"] == "hello"

    def test_request_with_system_prompt(self):
        req = LlmRequest(
            messages=[{"role": "user", "content": "hello"}],
            system_prompt="You are helpful",
        )
        msgs = req.get_messages()
        assert len(msgs) == 2
        assert msgs[0]["role"] == "system"

    def test_usage_defaults(self):
        usage = LlmUsage()
        assert usage.input_tokens == 0
        assert usage.output_tokens == 0


class TestProviderRegistry:
    """供应商注册表测试"""

    def test_empty_registry(self):
        from app.gateway.providers.provider_registry import ProviderRegistry
        registry = ProviderRegistry()
        assert registry.get() is None
        assert registry.get_default() is None

    def test_register_provider(self):
        from app.gateway.providers.provider_registry import ProviderRegistry
        from app.config import LlmProviderConfig
        registry = ProviderRegistry()
        cfg = LlmProviderConfig(
            name="test",
            base_url="http://localhost",
            api_key="test-key",
            model="test-model",
            is_default=True,
        )
        registry.register(cfg)
        assert registry.get("test") is not None
        assert registry.get_default() is not None
