"""
对话质量评估器

评估维度：
1. 相关性 (relevance): 回复是否回答了用户问题
2. 准确性 (accuracy): 信息是否正确
3. 完整性 (completeness): 是否包含必要信息
4. 流畅性 (fluency): 语言是否自然
5. 幻觉检测 (hallucination): 是否包含虚构信息
"""

import json
import re
import logging
from app.gateway.llm_gateway import llm_gateway
from app.gateway.schemas import LlmRequest
from app.db.connection import db_manager
from app.quality.schemas import QualityScore, QualityStats
from sqlalchemy import text

logger = logging.getLogger("tmap-python.quality.evaluator")

EVALUATE_PROMPT = """请评估以下 AI 对话的质量。

**用户问题**：{user_message}

**AI 回复**：{assistant_reply}

**意图类型**：{intent_type}

请从以下维度评分（0-1）并检测问题：
1. relevance: 回复是否切题
2. accuracy: 信息是否准确
3. completeness: 是否完整回答
4. fluency: 语言是否流畅自然
5. is_hallucination: 是否有虚构信息（如编造站名、票价）
6. is_off_topic: 是否偏离主题

严格输出 JSON：
```json
{{
  "relevance": 0.9,
  "accuracy": 0.8,
  "completeness": 0.7,
  "fluency": 0.9,
  "is_hallucination": false,
  "is_off_topic": false,
  "reason": "简要说明"
}}
```"""


class QualityEvaluator:
    """对话质量评估器"""

    async def evaluate(
        self,
        message_id: int,
        session_id: int,
        user_id: int | None,
        user_message: str,
        assistant_reply: str,
        intent_type: str,
        scenario: str,
        response_time_ms: int,
        token_count: int,
    ) -> QualityScore:
        """
        评估单条对话质量。

        使用轻量 LLM 进行自动评分。
        """
        score = QualityScore(
            message_id=message_id,
            session_id=session_id,
            user_id=user_id,
            response_time_ms=response_time_ms,
            token_count=token_count,
            intent_type=intent_type,
            scenario=scenario,
        )

        # 跳过过短的回复
        if len(assistant_reply) < 10:
            score.quality_score = 0.5
            await self._save_score(score)
            return score

        try:
            prompt = EVALUATE_PROMPT.format(
                user_message=user_message[:500],
                assistant_reply=assistant_reply[:1000],
                intent_type=intent_type,
            )

            reply = await llm_gateway.complete(LlmRequest(
                messages=[{"role": "user", "content": prompt}],
                system_prompt="你是对话质量评估专家，只输出 JSON。",
                temperature=0.1,
                max_tokens=300,
                json_mode=True,
            ))

            result = self._parse_json(reply.content)
            if result:
                # 计算综合评分
                dimensions = [
                    result.get("relevance", 0.5),
                    result.get("accuracy", 0.5),
                    result.get("completeness", 0.5),
                    result.get("fluency", 0.5),
                ]
                score.quality_score = round(sum(dimensions) / len(dimensions), 2)
                score.is_hallucination = result.get("is_hallucination", False)
                score.is_off_topic = result.get("is_off_topic", False)

                logger.debug(
                    f"质量评分: msg={message_id}, score={score.quality_score}, "
                    f"hallucination={score.is_hallucination}"
                )

        except Exception as e:
            logger.warning(f"质量评估失败: {e}")
            score.quality_score = 0.5  # 默认中等分

        await self._save_score(score)
        return score

    async def submit_feedback(
        self,
        message_id: int,
        feedback: str,
        detail: str = "",
        user_id: int | None = None,
        session_id: int | None = None,
    ):
        """提交用户反馈"""
        async with db_manager.get_session() as session:
            # 更新已有记录或插入新记录
            result = await session.execute(
                text("SELECT id FROM chat_quality_log WHERE message_id = :msg_id"),
                {"msg_id": message_id},
            )
            row = result.first()

            if row:
                await session.execute(
                    text("""UPDATE chat_quality_log
                            SET user_feedback = :feedback, feedback_detail = :detail
                            WHERE message_id = :msg_id"""),
                    {"msg_id": message_id, "feedback": feedback, "detail": detail},
                )
            else:
                await session.execute(
                    text("""INSERT INTO chat_quality_log
                            (message_id, session_id, user_id, user_feedback, feedback_detail, created_at)
                            VALUES (:msg_id, :session_id, :user_id, :feedback, :detail, NOW())"""),
                    {
                        "msg_id": message_id,
                        "session_id": session_id or 0,
                        "user_id": user_id,
                        "feedback": feedback,
                        "detail": detail,
                    },
                )
            await session.commit()

        logger.info(f"用户反馈已记录: msg={message_id}, feedback={feedback}")

    async def get_stats(self, days: int = 7) -> QualityStats:
        """获取质量统计"""
        async with db_manager.get_session() as session:
            # 总量
            result = await session.execute(
                text("""SELECT COUNT(*) as total,
                        SUM(CASE WHEN user_feedback = 'positive' THEN 1 ELSE 0 END) as positive,
                        SUM(CASE WHEN user_feedback = 'negative' THEN 1 ELSE 0 END) as negative,
                        AVG(response_time_ms) as avg_time,
                        AVG(quality_score) as avg_score,
                        SUM(CASE WHEN is_hallucination = 1 THEN 1 ELSE 0 END) as hallucination
                        FROM chat_quality_log
                        WHERE created_at >= DATE_SUB(NOW(), INTERVAL :days DAY)"""),
                {"days": days},
            )
            row = result.mappings().first()

            total = row["total"] or 0
            positive = row["positive"] or 0
            negative = row["negative"] or 0

            stats = QualityStats(
                total_conversations=total,
                positive_count=positive,
                negative_count=negative,
                positive_rate=round(positive / max(total, 1), 3),
                avg_response_time_ms=int(row["avg_time"] or 0),
                avg_quality_score=round(float(row["avg_score"] or 0), 2),
                hallucination_count=row["hallucination"] or 0,
                hallucination_rate=round((row["hallucination"] or 0) / max(total, 1), 3),
            )

            # 按意图分组
            result = await session.execute(
                text("""SELECT intent_type,
                        COUNT(*) as cnt,
                        AVG(quality_score) as avg_s
                        FROM chat_quality_log
                        WHERE created_at >= DATE_SUB(NOW(), INTERVAL :days DAY)
                        AND intent_type IS NOT NULL AND intent_type != ''
                        GROUP BY intent_type"""),
                {"days": days},
            )
            for row in result.mappings().all():
                stats.by_intent[row["intent_type"]] = {
                    "count": row["cnt"],
                    "avg_score": round(float(row["avg_s"] or 0), 2),
                }

            return stats

    async def get_low_score(self, page: int = 1, size: int = 20) -> list[dict]:
        """获取低分对话列表"""
        async with db_manager.get_session() as session:
            result = await session.execute(
                text("""SELECT q.*, cm.content as user_message, cm2.content as assistant_reply
                        FROM chat_quality_log q
                        LEFT JOIN chat_message cm ON q.message_id = cm.id
                        LEFT JOIN chat_message cm2 ON cm2.session_id = q.session_id
                            AND cm2.role = 'assistant' AND cm2.create_time > cm.create_time
                        WHERE q.quality_score < 0.6 OR q.is_hallucination = 1 OR q.user_feedback = 'negative'
                        ORDER BY q.created_at DESC
                        LIMIT :limit OFFSET :offset"""),
                {"limit": size, "offset": (page - 1) * size},
            )
            return [dict(row) for row in result.mappings().all()]

    async def _save_score(self, score: QualityScore):
        """保存评分到数据库"""
        try:
            async with db_manager.get_session() as session:
                await session.execute(
                    text("""INSERT INTO chat_quality_log
                            (message_id, session_id, user_id, quality_score,
                             response_time_ms, token_count, intent_type, scenario,
                             is_hallucination, is_off_topic, created_at)
                            VALUES (:msg_id, :session_id, :user_id, :score,
                                    :resp_time, :tokens, :intent, :scenario,
                                    :hallucination, :off_topic, NOW())
                            ON DUPLICATE KEY UPDATE
                                quality_score = VALUES(quality_score),
                                is_hallucination = VALUES(is_hallucination),
                                is_off_topic = VALUES(is_off_topic)"""),
                    {
                        "msg_id": score.message_id,
                        "session_id": score.session_id,
                        "user_id": score.user_id,
                        "score": score.quality_score,
                        "resp_time": score.response_time_ms,
                        "tokens": score.token_count,
                        "intent": score.intent_type,
                        "scenario": score.scenario,
                        "hallucination": score.is_hallucination,
                        "off_topic": score.is_off_topic,
                    },
                )
                await session.commit()
        except Exception as e:
            logger.warning(f"保存质量评分失败: {e}")

    def _parse_json(self, content: str) -> dict | None:
        if not content:
            return None
        try:
            return json.loads(content)
        except json.JSONDecodeError:
            match = re.search(r"```(?:json)?\s*\n?(.*?)\n?```", content, re.DOTALL)
            if match:
                try:
                    return json.loads(match.group(1))
                except json.JSONDecodeError:
                    pass
        return None


# 全局单例
quality_evaluator = QualityEvaluator()
