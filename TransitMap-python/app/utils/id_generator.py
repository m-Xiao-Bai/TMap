"""
ID 生成器

当数据库表没有设置 AUTO_INCREMENT 时，手动生成唯一 ID。
使用雪花算法简化版：时间戳 + 随机数。
"""

import time
import random

# 机器 ID（0-1023），可以配置
_machine_id = random.randint(0, 1023)
_sequence = 0
_last_timestamp = 0


def generate_id() -> int:
    """
    生成唯一 ID（类似雪花算法）。

    格式：41 位时间戳 + 10 位机器 ID + 13 位序列号
    保证在同一毫秒内不会重复。
    """
    global _sequence, _last_timestamp

    timestamp = int(time.time() * 1000)  # 毫秒级时间戳

    if timestamp == _last_timestamp:
        _sequence = (_sequence + 1) & 0x1FFF  # 13 位序列号
        if _sequence == 0:
            # 序列号溢出，等待下一毫秒
            while timestamp <= _last_timestamp:
                timestamp = int(time.time() * 1000)
    else:
        _sequence = 0

    _last_timestamp = timestamp

    # 时间戳左移 23 位，机器 ID 左移 13 位，加上序列号
    # 使用 2020-01-01 作为纪元
    epoch = 1577836800000  # 2020-01-01 00:00:00 UTC
    ts = (timestamp - epoch) & 0x1FFFFFFFFFF  # 41 位

    id_val = (ts << 23) | (_machine_id << 13) | _sequence

    # 确保是正整数且不超过 BIGINT 范围
    return id_val & 0x7FFFFFFFFFFFFFFF
