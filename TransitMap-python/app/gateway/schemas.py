"""
LLM 网关 — 数据模型
"""

from dataclasses import dataclass, field
from typing import AsyncGenerator


@dataclass
class LlmMessage:
    """单条消息"""
    role: str  # "system" | "user" | "assistant"
    content: str


@dataclass
class LlmRequest:
    """LLM 请求"""
    messages: list[LlmMessage] | list[dict]
    model: str = ""           # 空则用默认模型
    system_prompt: str = ""   # 可选，会拼到 messages 前面
    temperature: float = 0.3
    max_tokens: int = 1024
    timeout_ms: int = 30000
    provider: str = ""        # 指定供应商，空则用默认
    json_mode: bool = False   # 是否要求 JSON 输出

    def get_messages(self) -> list[dict]:
        """规范化消息列表"""
        msgs = []
        if self.system_prompt:
            msgs.append({"role": "system", "content": self.system_prompt})
        for m in self.messages:
            if isinstance(m, dict):
                msgs.append(m)
            else:
                msgs.append({"role": m.role, "content": m.content})
        return msgs


@dataclass
class LlmUsage:
    """Token 用量"""
    input_tokens: int = 0
    output_tokens: int = 0


@dataclass
class LlmReply:
    """LLM 同步响应"""
    content: str
    usage: LlmUsage = field(default_factory=LlmUsage)
    provider: str = ""
    model: str = ""


@dataclass
class LlmStreamChunk:
    """LLM 流式响应单个 chunk"""
    delta: str = ""           # 增量文本
    finish_reason: str = ""   # "stop" | "length" | ""
    usage: LlmUsage | None = None  # 仅最后一个 chunk 包含
