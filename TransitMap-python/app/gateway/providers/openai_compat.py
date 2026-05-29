"""
LLM 供应商 — OpenAI-Compatible 通用实现

支持所有兼容 OpenAI API 的供应商：
- DeepSeek
- 通义千问 (Qwen)
- Kimi (Moonshot)
- 豆包 (Doubao)
- 智谱 (Zhipu)
- SiliconFlow
- OpenAI
"""

import json
import logging
from typing import AsyncGenerator

import httpx

from app.gateway.providers.base import BaseLlmProvider
from app.gateway.schemas import LlmRequest, LlmReply, LlmStreamChunk, LlmUsage

logger = logging.getLogger("tmap-python.llm")


class OpenAICompatProvider(BaseLlmProvider):
    """OpenAI-Compatible 通用 LLM 供应商"""

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._client: httpx.AsyncClient | None = None

    def _get_endpoint(self) -> str:
        """
        获取 API 端点路径。

        智能判断：
        - 如果 base_url 已经包含路径（如 /anthropic），使用它
        - 如果 base_url 是标准 OpenAI 格式，追加 /chat/completions
        """
        # 如果 base_url 已经以常见路径结尾，直接使用
        if self.base_url.endswith("/chat/completions"):
            return self.base_url
        if self.base_url.endswith("/v1/messages"):
            return self.base_url
        # 默认追加 /chat/completions
        return self.base_url.rstrip("/") + "/chat/completions"

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                timeout=httpx.Timeout(self.timeout_ms / 1000),
            )
        return self._client

    def _build_payload(self, request: LlmRequest, stream: bool = False) -> dict:
        """构建请求 payload"""
        messages = request.get_messages()
        payload = {
            "model": request.model or self.model,
            "messages": messages,
            "temperature": request.temperature if request.temperature is not None else self.temperature,
            "max_tokens": request.max_tokens or self.max_tokens,
            "stream": stream,
        }
        if request.json_mode:
            payload["response_format"] = {"type": "json_object"}
        return payload

    async def complete(self, request: LlmRequest) -> LlmReply:
        """同步调用"""
        client = await self._get_client()
        payload = self._build_payload(request, stream=False)

        try:
            endpoint = self._get_endpoint()
            resp = await client.post(endpoint, json=payload)
            resp.raise_for_status()
            data = resp.json()

            content = data["choices"][0]["message"]["content"]
            usage_data = data.get("usage", {})
            usage = LlmUsage(
                input_tokens=usage_data.get("prompt_tokens", 0),
                output_tokens=usage_data.get("completion_tokens", 0),
            )
            return LlmReply(
                content=content,
                usage=usage,
                provider=self.name,
                model=data.get("model", self.model),
            )
        except httpx.HTTPStatusError as e:
            logger.error(f"[{self.name}] HTTP 错误: {e.response.status_code} — {e.response.text}")
            raise
        except Exception as e:
            logger.error(f"[{self.name}] 调用失败: {e}")
            raise

    async def complete_stream(self, request: LlmRequest) -> AsyncGenerator[LlmStreamChunk, None]:
        """流式调用"""
        client = await self._get_client()
        payload = self._build_payload(request, stream=True)

        try:
            endpoint = self._get_endpoint()
            async with client.stream("POST", endpoint, json=payload) as resp:
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if not line.startswith("data: "):
                        continue
                    data_str = line[6:]
                    if data_str.strip() == "[DONE]":
                        break
                    try:
                        data = json.loads(data_str)
                        delta = data["choices"][0].get("delta", {})
                        content = delta.get("content", "")
                        finish_reason = data["choices"][0].get("finish_reason", "")

                        chunk = LlmStreamChunk(
                            delta=content,
                            finish_reason=finish_reason or "",
                        )

                        # 提取 usage（如果有）
                        if "usage" in data and data["usage"]:
                            chunk.usage = LlmUsage(
                                input_tokens=data["usage"].get("prompt_tokens", 0),
                                output_tokens=data["usage"].get("completion_tokens", 0),
                            )

                        yield chunk
                    except json.JSONDecodeError:
                        continue
        except httpx.HTTPStatusError as e:
            logger.error(f"[{self.name}] 流式 HTTP 错误: {e.response.status_code}")
            raise
        except Exception as e:
            logger.error(f"[{self.name}] 流式调用失败: {e}")
            raise

    async def close(self):
        """关闭 HTTP 客户端"""
        if self._client and not self._client.is_closed:
            await self._client.aclose()
