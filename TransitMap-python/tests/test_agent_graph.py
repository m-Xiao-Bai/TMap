"""
Agent 图测试
"""

import pytest
from app.agent.nodes.classify import classify_node
from app.agent.nodes.intent_extract import _regex_extract


class TestClassifyNode:
    """消息分类测试"""

    @pytest.mark.asyncio
    async def test_classify_route(self):
        state = {"user_message": "从国贸到北京西站怎么走", "chat_history": []}
        result = await classify_node(state)
        assert result["intent"] == "route"

    @pytest.mark.asyncio
    async def test_classify_route_pattern2(self):
        state = {"user_message": "去天安门怎么走", "chat_history": []}
        result = await classify_node(state)
        assert result["intent"] == "route"

    @pytest.mark.asyncio
    async def test_classify_chat(self):
        state = {"user_message": "你好", "chat_history": []}
        result = await classify_node(state)
        assert result["intent"] == "chat"

    @pytest.mark.asyncio
    async def test_classify_chat_city_question(self):
        state = {"user_message": "南昌地铁有多少条线路", "chat_history": []}
        result = await classify_node(state)
        assert result["intent"] == "chat"

    @pytest.mark.asyncio
    async def test_classify_order(self):
        state = {
            "user_message": "下单",
            "chat_history": [
                {"extras": {"kind": "ROUTE_CARD", "payload": {}}},
            ],
        }
        result = await classify_node(state)
        assert result["intent"] == "order"

    @pytest.mark.asyncio
    async def test_classify_empty_message(self):
        state = {"user_message": "", "chat_history": []}
        result = await classify_node(state)
        assert result["intent"] == "chat"


class TestRegexExtract:
    """正则提取测试"""

    def test_extract_from_to(self):
        result = _regex_extract("从国贸到北京西站")
        assert result["slot_from"] == "国贸"
        assert result["slot_to"] == "北京西站"

    def test_extract_go_to(self):
        result = _regex_extract("去天安门怎么走")
        assert result["slot_from"] is None
        assert result["slot_to"] == "天安门"

    def test_extract_no_match(self):
        result = _regex_extract("你好")
        assert result["slot_from"] is None
        assert result["slot_to"] is None


class TestExtractChips:
    """快捷词提取测试"""

    def test_extract_json_array(self):
        from app.agent.nodes.general_chat import _extract_chips
        reply = "这是回复\n[\"路线规划\",\"附近站点\",\"换乘方案\"]"
        chips = _extract_chips(reply)
        assert chips == ["路线规划", "附近站点", "换乘方案"]

    def test_extract_no_chips(self):
        from app.agent.nodes.general_chat import _extract_chips
        reply = "这是回复"
        chips = _extract_chips(reply)
        assert len(chips) == 3  # 默认快捷词

    def test_remove_chips(self):
        from app.agent.nodes.general_chat import _remove_chips
        reply = "这是回复\n[\"路线规划\"]"
        cleaned = _remove_chips(reply)
        assert cleaned == "这是回复"
