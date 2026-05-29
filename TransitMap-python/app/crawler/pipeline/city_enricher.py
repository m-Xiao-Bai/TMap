"""
爬虫管线 — 城市 + 线路信息补充器

使用 LLM 获取城市基本信息和地铁线路数据。
返回数据严格匹配数据库表结构。
"""

import json
import re
import logging
from datetime import date
from app.gateway.llm_gateway import llm_gateway
from app.gateway.schemas import LlmRequest

logger = logging.getLogger("tmap-python.crawler.city_enricher")

# ============================================================
# 城市信息提示词（严格匹配 city 表结构）
# ============================================================
CITY_PROMPT = """你是城市轨道交通信息专家。请提供「{city_name}」截止到 {today} 的地铁城市信息。

【返回格式】严格按以下 JSON 返回，字段名和类型必须完全一致，不要添加额外字段：
```json
{{
  "city_name": "南宁市",
  "city_name_en": "Nanning",
  "city_alias": "邕城",
  "metro_line_logo": null,
  "metro_count": 0,
  "metro_line_count": 5,
  "hsr_count": 0,
  "metro_km": 128.20,
  "hsr_km": 0.00,
  "population": 874
}}
```

【字段说明】
- city_name: 城市中文名（必填）
- city_name_en: 城市英文名（必填，纯英文）
- city_alias: 城市别称（如蓉城、羊城、邕城，没有则填 null）
- metro_line_logo: 线路图 logo URL（一般填 null）
- metro_count: 地铁站点总数（整数）
- metro_line_count: 地铁线路总数（整数）
- hsr_count: 高铁站数量（整数，不确定填 0）
- metro_km: 地铁总里程（单位：公里，保留2位小数）
- hsr_km: 高铁总里程（单位：公里，保留2位小数，不确定填 0.00）
- population: 常住人口（单位：万人，整数）

【注意】
1. 数据必须是截止到 {today} 的最新数据
2. 不确定的数值字段填 0，不确定的字符串字段填 null
3. 所有数字不要带单位，只填纯数字
4. 只输出 JSON，不要输出其他内容"""


# ============================================================
# 线路信息提示词（严格匹配 metro_line 表结构）
# ============================================================
LINES_PROMPT = """你是城市轨道交通信息专家。请提供「{city_name}」截止到 {today} 的所有已开通地铁线路。

【返回格式】严格按以下 JSON 数组返回，字段名和类型必须完全一致：
```json
[
  {{
    "line_name": "1号线",
    "line_no": "1",
    "line_color": "#c23a30",
    "line_color_cn": "红色",
    "station_count": 24,
    "total_km": 32.10,
    "open_date": "2016-06-28",
    "first_time": "06:30",
    "last_time": "23:00"
  }},
  {{
    "line_name": "2号线",
    "line_no": "2",
    "line_color": "#e6a23c",
    "line_color_cn": "橙色",
    "station_count": 18,
    "total_km": 27.30,
    "open_date": "2017-12-28",
    "first_time": "06:30",
    "last_time": "23:00"
  }}
]
```

【字段说明】
- line_name: 线路名称（必填，如"1号线"、"环线"）
- line_no: 线路编号（必填，如"1"、"2"、"环"）
- line_color: 线路标识色（十六进制颜色码，如 #c23a30，不确定填 #000000）
- line_color_cn: 颜色中文名（如"红色"、"蓝色"，不确定填 null）
- station_count: 站点数量（整数）
- total_km: 线路总里程（单位：公里，保留2位小数）
- open_date: 开通日期（格式：YYYY-MM-DD，不确定填 null）
- first_time: 首班车时间（格式：HH:MM，不确定填 null）
- last_time: 末班车时间（格式：HH:MM，不确定填 null）

【注意】
1. 只列出已开通的线路，不要列出规划/在建线路
2. 所有数字不要带单位
3. 只输出 JSON 数组，不要输出其他内容"""


async def enrich_city_with_llm(city_name: str) -> dict:
    """
    使用 LLM 获取城市信息。

    Returns:
        城市数据字典（匹配 city 表结构），失败返回 {"_source": "llm_failed"}
    """
    today = date.today().isoformat()
    prompt = CITY_PROMPT.format(city_name=city_name, today=today)

    try:
        reply = await llm_gateway.complete(LlmRequest(
            messages=[{"role": "user", "content": prompt}],
            system_prompt=f"你是城市轨道交通信息专家。当前日期：{today}。只输出 JSON，不要输出其他内容。",
            temperature=0.1,
            max_tokens=500,
            json_mode=True,
        ))

        data = _parse_json(reply.content)
        if not data:
            logger.warning(f"LLM 城市信息解析失败: {city_name}")
            return {"_source": "llm_failed", "_error": "JSON 解析失败"}

        # 校验并规范化
        data = _validate_city_data(data)
        data["_source"] = "llm"
        data["_llm_date"] = today
        data["_confidence"] = data.get("_confidence", "medium")

        logger.info(f"LLM 城市信息: {city_name} → {json.dumps(data, ensure_ascii=False)}")
        return data

    except Exception as e:
        logger.error(f"LLM 城市信息获取失败: {e}")
        return {"_source": "llm_failed", "_error": str(e)}


async def enrich_lines_with_llm(city_name: str) -> list[dict]:
    """
    使用 LLM 获取城市地铁线路列表。

    Returns:
        线路数据列表（匹配 metro_line 表结构），失败返回空列表
    """
    today = date.today().isoformat()
    prompt = LINES_PROMPT.format(city_name=city_name, today=today)

    try:
        reply = await llm_gateway.complete(LlmRequest(
            messages=[{"role": "user", "content": prompt}],
            system_prompt=f"你是城市轨道交通信息专家。当前日期：{today}。只输出 JSON 数组，不要输出其他内容。",
            temperature=0.1,
            max_tokens=2000,
            json_mode=True,
        ))

        raw_content = reply.content
        data = _parse_json(raw_content)
        if data is None:
            logger.warning(f"LLM 线路信息 JSON 解析失败: {city_name}")
            logger.warning(f"LLM 原始返回内容: {raw_content[:2000]}")
            return []
        if not isinstance(data, list):
            # 如果返回的是 dict 而不是 list，尝试包装
            if isinstance(data, dict) and "lines" in data:
                data = data["lines"]
            elif isinstance(data, dict) and "line_name" in data:
                data = [data]
            else:
                logger.warning(f"LLM 线路信息返回格式异常: {city_name}, 类型={type(data).__name__}")
                logger.warning(f"LLM 原始返回内容: {raw_content[:2000]}")
                return []

        # 校验每条线路
        validated = []
        for item in data:
            if not item.get("line_name"):
                continue
            item = _validate_line_data(item)
            item["_source"] = "llm"
            item["_llm_date"] = today
            validated.append(item)

        logger.info(f"LLM 线路信息: {city_name} → {len(validated)} 条线路")
        for v in validated:
            logger.info(f"  {v['line_name']}: color={v.get('line_color')}, stations={v.get('station_count')}, km={v.get('total_km')}")
        return validated

    except Exception as e:
        logger.error(f"LLM 线路信息获取失败: {e}")
        return []


def _validate_city_data(data: dict) -> dict:
    """校验城市数据，确保类型正确"""
    result = {
        "city_name": str(data.get("city_name", "")),
        "city_name_en": str(data.get("city_name_en", "")) or None,
        "city_alias": str(data.get("city_alias", "")) or None,
        "metro_line_logo": data.get("metro_line_logo"),
        "metro_count": _to_int(data.get("metro_count"), 0, 500),
        "metro_line_count": _to_int(data.get("metro_line_count"), 0, 50),
        "hsr_count": _to_int(data.get("hsr_count"), 0, 100),
        "metro_km": _to_decimal(data.get("metro_km"), 0, 1000),
        "hsr_km": _to_decimal(data.get("hsr_km"), 0, 5000),
        "population": _to_int(data.get("population"), 0, 5000),
    }

    # 英文名校验
    if result["city_name_en"] and not re.match(r"^[A-Za-z\s\-']+$", result["city_name_en"]):
        result["city_name_en"] = None

    # 置信度判断
    if result["metro_line_count"] > 0 and result["metro_count"] > 0:
        result["_confidence"] = "high"
    elif result["metro_line_count"] > 0 or result["metro_count"] > 0:
        result["_confidence"] = "medium"
    else:
        result["_confidence"] = "low"

    return result


def _validate_line_data(data: dict) -> dict:
    """校验线路数据，确保类型正确"""
    line_name = str(data.get("line_name", ""))
    line_no = str(data.get("line_no", ""))

    # 如果没有编号，从线路名提取
    if not line_no:
        match = re.search(r"([0-9a-zA-Z]+)", line_name)
        line_no = match.group(1) if match else line_name

    # 颜色校验
    line_color = str(data.get("line_color", ""))
    if not re.match(r"^#[0-9a-fA-F]{6}$", line_color):
        line_color = "#000000"

    return {
        "line_name": line_name,
        "line_no": line_no,
        "line_color": line_color,
        "line_color_cn": str(data.get("line_color_cn", "")) or None,
        "station_count": _to_int(data.get("station_count"), 1, 200),
        "total_km": _to_decimal(data.get("total_km"), 0.1, 200),
        "open_date": _validate_date(data.get("open_date")),
        "first_time": _validate_time(data.get("first_time")),
        "last_time": _validate_time(data.get("last_time")),
    }


def _to_int(value, min_val=0, max_val=99999) -> int | None:
    """转整数"""
    if value is None:
        return None
    try:
        v = int(float(str(value).replace(",", "")))
        return v if min_val <= v <= max_val else None
    except (ValueError, TypeError):
        return None


def _to_decimal(value, min_val=0, max_val=99999) -> float | None:
    """转小数"""
    if value is None:
        return None
    try:
        v = float(str(value).replace(",", "").replace("km", "").replace("公里", ""))
        return round(v, 2) if min_val <= v <= max_val else None
    except (ValueError, TypeError):
        return None


def _validate_date(value) -> str | None:
    """校验日期格式 YYYY-MM-DD"""
    if not value:
        return None
    s = str(value).strip()
    if re.match(r"^\d{4}-\d{2}-\d{2}$", s):
        return s
    return None


def _validate_time(value) -> str | None:
    """校验时间格式 HH:MM"""
    if not value:
        return None
    s = str(value).strip()
    if re.match(r"^\d{2}:\d{2}$", s):
        return s
    return None


def _parse_json(content: str):
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
