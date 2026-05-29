"""
Agent 上下文管理器

维护对话状态，包括：
- 当前城市
- 最近提到的地点
- 对话历史摘要
- 用户偏好
"""

import json
import logging
from dataclasses import dataclass, field
from app.clients.java_api import java_client

logger = logging.getLogger("tmap-python.agent.context")


@dataclass
class ConversationContext:
    """对话上下文"""
    session_id: str = ""
    user_id: int | None = None
    user_message: str = ""
    lat: float = 0.0
    lng: float = 0.0

    # 对话状态
    current_city: str = ""           # 当前城市名
    current_city_id: int | None = None  # 当前城市 ID
    recent_locations: list[str] = field(default_factory=list)  # 最近提到的地点
    recent_from: str = ""            # 最近的出发地
    recent_to: str = ""              # 最近的目的地
    conversation_topic: str = ""     # 当前话题: route / chat / order
    last_route_plan: dict | None = None  # 最近的路线规划

    # 对话历史
    chat_history: list[dict] = field(default_factory=list)

    async def build_from_request(self, request_data: dict):
        """从请求数据构建上下文"""
        self.session_id = request_data.get("session_id", "")
        self.user_id = request_data.get("user_id")
        self.user_message = request_data.get("user_message", "")
        self.lat = request_data.get("lat", 0.0)
        self.lng = request_data.get("lng", 0.0)
        self.chat_history = request_data.get("chat_history", [])

        # 从对话历史中提取上下文
        await self._extract_from_history()

        # 如果没有城市，尝试从 GPS 推断
        if not self.current_city and self.lat != 0 and self.lng != 0:
            await self._infer_city_from_gps()

    async def _extract_from_history(self):
        """从对话历史中提取上下文信息"""
        for msg in self.chat_history[-10:]:  # 最近 10 条
            content = msg.get("content", "")
            extras = msg.get("extras", {})

            # 从路线卡片中提取城市
            if isinstance(extras, dict) and extras.get("kind") == "ROUTE_CARD":
                payload = extras.get("payload", {})
                if payload.get("cityName"):
                    self.current_city = payload["cityName"]
                if payload.get("startStationName"):
                    self.recent_from = payload["startStationName"]
                if payload.get("endStationName"):
                    self.recent_to = payload["endStationName"]
                self.last_route_plan = payload

            # 从消息中提取城市名
            if not self.current_city:
                import re
                city_match = re.search(r"([一-龥]{2,4})(?:地铁|轨道交通|公交)", content)
                if city_match:
                    self.current_city = city_match.group(1) + "市"

    async def _infer_city_from_gps(self):
        """从 GPS 坐标推断城市"""
        try:
            loc = await java_client.regeo(self.lng, self.lat)
            if loc and loc.get("city"):
                self.current_city = loc["city"]
                logger.info(f"从 GPS 推断城市: {self.current_city}")
        except Exception as e:
            logger.debug(f"GPS 推断城市失败: {e}")

    def update_after_route(self, route_plan: dict):
        """路线规划后更新上下文"""
        if route_plan:
            self.last_route_plan = route_plan
            if route_plan.get("startStationName"):
                self.recent_from = route_plan["startStationName"]
            if route_plan.get("endStationName"):
                self.recent_to = route_plan["endStationName"]
            if route_plan.get("cityName"):
                self.current_city = route_plan["cityName"]

    def to_dict(self) -> dict:
        """导出为字典（用于 LLM prompt）"""
        return {
            "current_city": self.current_city,
            "recent_from": self.recent_from,
            "recent_to": self.recent_to,
            "recent_locations": self.recent_locations[-5:],
            "conversation_topic": self.conversation_topic,
            "has_gps": self.lat != 0 and self.lng != 0,
            "gps": {"lat": self.lat, "lng": self.lng} if self.lat != 0 else None,
        }
