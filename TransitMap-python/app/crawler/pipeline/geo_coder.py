"""
爬虫管线 — Step 3: 高德坐标补全

对没有坐标的站点，通过高德 geocode API 获取坐标。
对已有坐标的站点，通过 regeo API 补全地址。
"""

import asyncio
import logging
from app.clients.amap_client import amap_client
from app.crawler.sources.comparator import MergedStation
from app.crawler.progress_tracker import progress_tracker

logger = logging.getLogger("tmap-python.crawler.pipeline.geo_coder")


async def geocode_stations(
    task_id: str,
    city_name: str,
    stations: list[MergedStation],
) -> list[MergedStation]:
    """
    为站点补全坐标和地址。

    - 没有坐标的 → 通过地址 geocode 获取坐标
    - 有坐标但没地址的 → 通过 regeo 获取地址
    - 两者都有的 → 跳过

    Args:
        task_id: 任务 ID
        city_name: 城市名称
        stations: 站点列表

    Returns:
        更新了坐标/地址的站点列表
    """
    need_geocode = [s for s in stations if s.lat == 0 or s.lng == 0]
    need_regeo = [s for s in stations if s.lat != 0 and s.lng != 0 and not s.address]
    skip = [s for s in stations if s.lat != 0 and s.lng != 0 and s.address]

    total = len(need_geocode) + len(need_regeo)
    if total == 0:
        await progress_tracker.update(
            task_id, 55, "geocoding",
            "所有站点已有坐标和地址，跳过地理编码",
        )
        return stations

    await progress_tracker.update(
        task_id, 40, "geocoding",
        f"需要地理编码: {len(need_geocode)} 个站点需要坐标, {len(need_regeo)} 个需要地址",
    )

    processed = 0

    # 1. 需要坐标的站点：通过地址 geocode
    for station in need_geocode:
        address = station.address or f"{city_name}{station.name}"
        result = await amap_client.geocode(address, city_name)
        if result:
            station.lat = result.lat
            station.lng = result.lng
            if not station.address and result.address:
                station.address = result.address
            processed += 1
            logger.debug(f"geocode: {station.name} → ({result.lat}, {result.lng})")
        else:
            logger.warning(f"geocode 失败: {station.name} ({address})")
        await asyncio.sleep(0.15)  # 限流

    # 2. 有坐标但没地址的站点：通过 regeo 获取地址
    for station in need_regeo:
        result = await amap_client.regeo(station.lng, station.lat)
        if result and result.address:
            station.address = result.address
            processed += 1
            logger.debug(f"regeo: {station.name} → {result.address}")
        await asyncio.sleep(0.15)

    await progress_tracker.update(
        task_id, 55, "geocoding",
        f"地理编码完成: 成功 {processed}/{total} 个站点",
    )

    return stations
