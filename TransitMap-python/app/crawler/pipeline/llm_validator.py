"""
爬虫管线 — Step 2: LLM 校验 + 地址补全

将爬取到的站点列表发给 LLM 进行：
1. 校验站点名称是否正确
2. 校验线路-站点归属关系
3. 补充详细地址
4. 标记 confidence
"""

import json
import logging
from string import Template
from app.gateway.llm_gateway import llm_gateway
from app.gateway.schemas import LlmRequest
from app.crawler.sources.comparator import MergedStation, MergedLine
from app.crawler.progress_tracker import progress_tracker

logger = logging.getLogger("tmap-python.crawler.pipeline.llm_validator")

VALIDATE_PROMPT = Template("""你是城市轨道交通数据校验专家。

以下是「$city」的地铁站点数据（来自多个数据源对比合并），请完成以下任务：

1. 校验站点名称是否正确（修正错别字、识别已关闭/规划中的站点）
2. 校验线路-站点归属关系是否合理
3. 为每个站点补充详细地址（XX市XX区XX路附近）
4. 标记每条数据的 confidence: high/medium/low

**输入数据**：
线路列表：
$lines_json

站点列表：
$stations_json

**输出要求**：
请严格输出 JSON 格式，不要输出其他内容：
```json
{
  "validated_stations": [
    {
      "name": "站点名",
      "corrected_name": "修正后站名（无修正则同 name）",
      "line_names": ["线路1", "线路2"],
      "address": "XX市XX区XX路附近",
      "confidence": "high",
      "corrections": "修正说明（无修正为空）"
    }
  ],
  "line_corrections": [
    {
      "line_name": "线路名",
      "issue": "问题描述",
      "suggestion": "建议"
    }
  ],
  "summary": "校验总结"
}
```""")


async def validate_with_llm(
    task_id: str,
    city_name: str,
    stations: list[MergedStation],
    lines: list[MergedLine],
) -> list[MergedStation]:
    """
    使用 LLM 校验站点数据并补全地址。

    Args:
        task_id: 任务 ID
        city_name: 城市名称
        stations: 合并后的站点列表
        lines: 合并后的线路列表

    Returns:
        校验后的站点列表（更新了 address 和 confidence）
    """
    if not stations:
        return stations

    await progress_tracker.update(
        task_id, 25, "llm_validating",
        f"正在进行 LLM 数据校验（{len(stations)} 个站点）...",
    )

    # 准备输入数据
    lines_json = json.dumps(
        [{"name": l.name, "stations": l.stations} for l in lines],
        ensure_ascii=False, indent=2,
    )
    stations_json = json.dumps(
        [{"name": s.name, "line_names": s.line_names, "lat": s.lat, "lng": s.lng} for s in stations],
        ensure_ascii=False, indent=2,
    )

    prompt = VALIDATE_PROMPT.safe_substitute(
        city=city_name,
        lines_json=lines_json,
        stations_json=stations_json,
    )

    try:
        reply = await llm_gateway.complete(LlmRequest(
            messages=[{"role": "user", "content": prompt}],
            system_prompt="你是城市轨道交通数据校验专家，只输出 JSON。",
            temperature=0.1,
            max_tokens=4096,
            json_mode=True,
        ))

        # 解析 LLM 输出
        validated = _parse_llm_output(reply.content)
        if not validated:
            logger.warning("LLM 校验输出解析失败，使用原始数据")
            return stations

        # 合并校验结果到站点数据
        validated_map = {}
        for v in validated.get("validated_stations", []):
            name = v.get("corrected_name") or v.get("name", "")
            key = name.rstrip("站") if name else ""
            validated_map[key] = v

        updated_count = 0
        for station in stations:
            key = station.name.rstrip("站")
            if key in validated_map:
                v = validated_map[key]
                # 更新地址
                if v.get("address"):
                    station.address = v["address"]
                    updated_count += 1
                # 更新 confidence（LLM 给出的优先级低于多源对比）
                llm_conf = v.get("confidence", "")
                if llm_conf == "low" and station.confidence != "low":
                    # LLM 标记为 low 但多源对比为 high/medium，保留原值
                    pass
                elif llm_conf == "high" and station.confidence == "low":
                    # LLM 确认为 high，提升置信度
                    station.confidence = "medium"

        await progress_tracker.update(
            task_id, 35, "llm_validating",
            f"LLM 校验完成: 更新了 {updated_count} 个站点的地址信息",
        )

    except Exception as e:
        logger.error(f"LLM 校验失败: {e}")
        await progress_tracker.update(
            task_id, 35, "llm_validating",
            f"LLM 校验失败（{e}），将使用原始数据继续",
        )

    return stations


def _parse_llm_output(content: str) -> dict | None:
    """解析 LLM 输出的 JSON"""
    if not content:
        return None

    # 尝试直接解析
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        pass

    # 尝试从 markdown 代码块中提取
    import re
    match = re.search(r"```(?:json)?\s*\n?(.*?)\n?```", content, re.DOTALL)
    if match:
        try:
            return json.loads(match.group(1))
        except json.JSONDecodeError:
            pass

    return None
