"""
Agent 状态定义

LangGraph 使用 TypedDict 定义状态图的状态结构。
所有节点共享同一个状态对象，通过 key 读写数据。
"""

from typing import TypedDict, Optional, Literal


class AgentState(TypedDict):
    """Agent 对话状态"""

    # ── 输入 ──
    user_message: str                    # 用户消息
    session_id: str                      # 会话 ID
    user_id: Optional[int]               # 用户 ID（可能为空）
    lat: float                           # 用户纬度
    lng: float                           # 用户经度
    chat_history: list[dict]             # 对话历史

    # ── 消息分类 ──
    intent: Literal["route", "chat", "order"]  # 意图类型

    # ── 路线规划 ──
    slot_from: Optional[str]             # 出发地（用户输入）
    slot_to: Optional[str]               # 目的地（用户输入）
    llm_inferred_city: Optional[str]     # LLM 推断的城市
    from_geo: Optional[dict]             # 出发地坐标 {"lat": ..., "lng": ...}
    to_geo: Optional[dict]               # 目的地坐标
    from_city_id: Optional[int]          # 出发地城市 ID
    to_city_id: Optional[int]            # 目的地城市 ID
    scenario: Optional[str]              # 场景: SAME_CITY / CROSS_CITY / NO_METRO / MISSING_DEST / SAME_STATION / NO_ROUTE
    from_station: Optional[dict]         # 最近出发站 {"id": ..., "name": ...}
    to_station: Optional[dict]           # 最近到达站
    route_plan: Optional[dict]           # 路线规划结果

    # ── 输出 ──
    reply: str                           # 回复文本
    cards: list[dict]                    # 卡片数据（ROUTE_CARD / ORDER_CARD）
    chips: list[str]                     # 快捷回复

    # ── 流控 ──
    short_circuit: bool                  # 是否提前终止
    tokens_in: int                       # 输入 token 数
    tokens_out: int                      # 输出 token 数
