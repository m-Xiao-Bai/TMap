"""
爬虫管线 — 城市信息补充器

使用 LLM 补充城市的基本信息（英文名、别名、人口等）。
核心原则：
- 只补充空字段，不覆盖已有数据
- 传入今天日期，要求截止到当前的数据
- 结构化 JSON 输出
- 标记数据来源为 "llm"
- LLM 失败时降级，不阻塞流程
"""

import json
import re
import logging
from datetime import date
from app.gateway.llm_gateway import llm_gateway
from app.gateway.schemas import LlmRequest

logger = logging.getLogger("tmap-python.crawler.city_enricher")

CITY_ENRICH_PROMPT = """请提供以下城市截止到 {today} 的地铁城市基本信息。

城市名称：{city_name}

请严格按以下 JSON 格式返回，不要输出其他内容：
```json
{{
  "city_name_en": "城市英文名",
  "city_alias": "城市别称（如蓉城、羊城等）",
  "population": "常住人口（万人，如 874 万）",
  "metro_km": "地铁总里程（公里，如 128.2）",
  "data_date": "{today}",
  "confidence": "high/medium/low（你对数据准确性的信心）"
}}
```

注意：
1. 数据必须是截止到 {today} 的最新数据
2. 如果不确定某项数据，请设为 null，confidence 设为 low
3. population 格式为数字（万人），如 874
4. metro_km 格式为数字（公里），如 128.2"""


async def enrich_city_with_llm(
    city_name: str,
    existing_data: dict = None,
) -> dict:
    """
    使用 LLM 补充城市信息。

    Args:
        city_name: 城市名称
        existing_data: 已有的城市数据（用于增量补充）

    Returns:
        补充后的城市数据字典
    """
    today = date.today().isoformat()
    prompt = CITY_ENRICH_PROMPT.format(city_name=city_name, today=today)

    try:
        reply = await llm_gateway.complete(LlmRequest(
            messages=[{"role": "user", "content": prompt}],
            system_prompt=f"你是城市信息专家。当前日期：{today}。只输出 JSON。",
            temperature=0.1,
            max_tokens=500,
            json_mode=True,
        ))

        llm_data = _parse_json(reply.content)
        if not llm_data:
            logger.warning(f"LLM 城市信息解析失败: {city_name}")
            return {}

        # 数据校验
        llm_data = _validate_city_data(llm_data)

        # 标记来源
        llm_data["_source"] = "llm"
        llm_data["_llm_date"] = today
        llm_data["_confidence"] = llm_data.get("confidence", "medium")

        # 增量补充：只更新空字段
        if existing_data:
            result = dict(existing_data)
            for key, value in llm_data.items():
                if key.startswith("_"):
                    result[key] = value
                elif not result.get(key) and value:
                    result[key] = value
            return result

        return llm_data

    except Exception as e:
        logger.error(f"LLM 城市信息补充失败: {e}")
        return {"_source": "llm_failed", "_error": str(e)}


async def enrich_cities_batch(
    city_names: list[str],
) -> dict[str, dict]:
    """
    批量补充城市信息（合并为一次 LLM 调用）。

    Args:
        city_names: 城市名称列表

    Returns:
        {city_name: city_data} 字典
    """
    if not city_names:
        return {}

    today = date.today().isoformat()
    cities_str = "\n".join(f"{i+1}. {name}" for i, name in enumerate(city_names))

    prompt = f"""请分别提供以下城市截止到 {today} 的地铁城市基本信息。

城市列表：
{cities_str}

请严格按以下 JSON 格式返回，每个城市一个对象：
```json
[
  {{
    "city_name": "城市名",
    "city_name_en": "英文名",
    "city_alias": "别称",
    "population": 874,
    "metro_km": 128.2,
    "confidence": "high/medium/low"
  }}
]
```

注意：
1. 数据必须是截止到 {today} 的最新数据
2. 不确定的字段设为 null
3. population 单位：万人
4. metro_km 单位：公里"""

    try:
        reply = await llm_gateway.complete(LlmRequest(
            messages=[{"role": "user", "content": prompt}],
            system_prompt=f"你是城市信息专家。当前日期：{today}。只输出 JSON 数组。",
            temperature=0.1,
            max_tokens=2000,
            json_mode=True,
        ))

        data_list = _parse_json(reply.content)
        if not isinstance(data_list, list):
            logger.warning("LLM 批量城市信息返回格式异常")
            return {}

        results = {}
        for item in data_list:
            city = item.get("city_name", "")
            if not city:
                continue
            validated = _validate_city_data(item)
            validated["_source"] = "llm"
            validated["_llm_date"] = today
            validated["_confidence"] = validated.get("confidence", "medium")
            results[city] = validated

        logger.info(f"批量补充城市信息: {len(results)}/{len(city_names)} 个成功")
        return results

    except Exception as e:
        logger.error(f"批量城市信息补充失败: {e}")
        return {}


def _validate_city_data(data: dict) -> dict:
    """校验城市数据合理性"""
    # 人口校验
    pop = data.get("population")
    if pop is not None:
        try:
            pop = float(str(pop).replace("万", "").replace(",", ""))
            if pop < 0 or pop > 5000:
                data["population"] = None
            else:
                data["population"] = round(pop, 1)
        except (ValueError, TypeError):
            data["population"] = None

    # 地铁里程校验
    km = data.get("metro_km")
    if km is not None:
        try:
            km = float(str(km).replace("km", "").replace(",", ""))
            if km < 0 or km > 1000:
                data["metro_km"] = None
            else:
                data["metro_km"] = round(km, 1)
        except (ValueError, TypeError):
            data["metro_km"] = None

    # 英文名校验
    en = data.get("city_name_en", "")
    if en and not re.match(r"^[A-Za-z\s\-']+$", str(en)):
        data["city_name_en"] = None

    return data


def _parse_json(content: str) -> dict | list | None:
    """解析 JSON"""
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
