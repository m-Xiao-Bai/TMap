#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
爬取多个城市地铁站数据（批量）
==============================
从 OpenStreetMap Overpass API 批量爬取多个城市的地铁站数据，
每个城市生成独立的 Excel 文件。

用法:
  python 爬取多个城市地铁站.py                    # 爬取预设全部城市
  python 爬取多个城市地铁站.py 北京市 上海市 广州市  # 爬取指定城市
  python 爬取多个城市地铁站.py --list               # 查看可用城市

Excel 列:
  国家|城市|站名|英文名|别称|经度|纬度|换乘|线路ID|线路名|出口数|厕所|类型|开通日期|首班|末班|状态码|扩展

依赖:
  pip install requests openpyxl
"""

import time
import json
import os
import re
import sys
from datetime import datetime

import requests
import openpyxl
from openpyxl.styles import Font, Alignment, PatternFill, Border, Side
from openpyxl.utils import get_column_letter


# ==================== 配置 ====================

OVERPASS_URL = 'https://overpass-api.de/api/interpreter'
REQUEST_INTERVAL = 2
COUNTRY_NAME = '中国'
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data', '地铁xlsx')

HEADERS = {
    'Accept': '*/*',
    'Accept-Language': 'zh-CN,zh;q=0.9',
    'Connection': 'keep-alive',
    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
    'Origin': 'https://overpass-turbo.eu',
    'Referer': 'https://overpass-turbo.eu/',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
}

# 查询模板：地铁/轻轨/单轨 站
QUERY_URBAN_RAIL = "data=[out:json];area[name='{city}']->.a;(node['railway'='station']['station'='subway'](area.a);node['railway'='station']['station'='light_rail'](area.a);node['railway'='station']['station'='monorail'](area.a);way['railway'='station']['station'='subway'](area.a);way['railway'='station']['station'='light_rail'](area.a);way['railway'='station']['station'='monorail'](area.a););out body;"

# 宽松模板
QUERY_RELAXED = "data=[out:json];area[name='{city}']->.a;(node['railway'='station']['subway'='yes'](area.a);node['railway'='station']['station'='subway'](area.a);node['railway'='station']['station'='light_rail'](area.a);node['railway'='station']['station'='monorail'](area.a);way['railway'='station']['station'='subway'](area.a);way['railway'='station']['station'='light_rail'](area.a);way['railway'='station']['station'='monorail'](area.a););out body;"

# ==================== 可用城市 ====================

CITIES = [
    '北京市', '天津市', '石家庄市', '太原市', '呼和浩特市',
    '沈阳市', '大连市', '长春市', '哈尔滨市',
    '上海市', '南京市', '无锡市', '徐州市', '常州市', '苏州市',
    '杭州市', '宁波市', '温州市', '嘉兴市', '绍兴市',
    '合肥市', '福州市', '厦门市', '南昌市', '济南市', '青岛市',
    '郑州市', '武汉市', '长沙市', '广州市', '深圳市', '珠海市',
    '佛山市', '东莞市', '南宁市', '海口市',
    '重庆市', '成都市', '贵阳市', '昆明市', '拉萨市',
    '西安市', '兰州市', '西宁市', '银川市', '乌鲁木齐市',
    '香港', '澳门', '台北',
]

CITIES_BY_REGION = [
    ('华北', ['北京市', '天津市', '石家庄市', '太原市', '呼和浩特市']),
    ('东北', ['沈阳市', '大连市', '长春市', '哈尔滨市']),
    ('华东', ['上海市', '南京市', '无锡市', '徐州市', '常州市', '苏州市',
              '杭州市', '宁波市', '温州市', '嘉兴市', '绍兴市',
              '合肥市', '福州市', '厦门市', '南昌市', '济南市', '青岛市']),
    ('中南', ['郑州市', '武汉市', '长沙市', '广州市', '深圳市', '珠海市',
              '佛山市', '东莞市', '南宁市', '海口市']),
    ('西南', ['重庆市', '成都市', '贵阳市', '昆明市', '拉萨市']),
    ('西北', ['西安市', '兰州市', '西宁市', '银川市', '乌鲁木齐市']),
    ('港澳台', ['香港', '澳门', '台北']),
]


# ==================== 核心函数 ====================

def query_city(city, relaxed=False):
    """查询 Overpass API，返回 (元素列表, 错误信息)"""
    template = QUERY_RELAXED if relaxed else QUERY_URBAN_RAIL
    payload = template.format(city=city)
    try:
        resp = requests.post(OVERPASS_URL, headers=HEADERS, data=payload, timeout=30)
        if resp.status_code != 200:
            return [], f'HTTP {resp.status_code}'
        return resp.json().get('elements', []), None
    except requests.Timeout:
        return [], '请求超时'
    except json.JSONDecodeError:
        return [], 'JSON解析失败'
    except Exception as e:
        return [], str(e)


def extract_stations(elements, city):
    """从 OSM 元素列表中提取车站信息"""
    results = []
    seen_keys = set()

    for elem in elements:
        tags = elem.get('tags') or {}
        if tags.get('usage') == 'freight':
            continue

        name = tags.get('name:zh') or tags.get('name') or ''
        if not name:
            continue

        name_en = tags.get('name:en', '')
        alias = tags.get('short_name', '')
        if not alias:
            alt = tags.get('alt_name', '')
            if alt:
                alias = re.split(r'[;；,，]', alt)[0].strip()

        lon = elem.get('lon')
        lat = elem.get('lat')
        if lon is None or lat is None:
            continue

        key = (city, name, round(float(lon), 6), round(float(lat), 6))
        if key in seen_keys:
            continue
        seen_keys.add(key)

        line = tags.get('line', '')
        is_transfer = 1 if line and re.search(r'[;；,，/]', line) else 0

        results.append({
            'country': COUNTRY_NAME,
            'city': city,
            'stationName': name,
            'stationNameEn': name_en,
            'stationAlias': alias,
            'longitude': round(float(lon), 6),
            'latitude': round(float(lat), 6),
            'isTransfer': is_transfer,
            'lineIds': '',
            'lineNames': line or tags.get('network', ''),
            'exitCount': 0,
            'hasToilet': 0,
            'stationType': 0,
            'openDate': '',
            'firstTime': '',
            'lastTime': '',
            'statusCode': 1,
            'extra': '',
        })

    return results


def save_to_excel(stations, output_path):
    """保存车站列表到 Excel 文件"""
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = '地铁站数据'

    headers = ['国家', '城市', '站名', '英文名', '别称', '经度', '纬度',
               '换乘', '线路ID', '线路名', '出口数', '厕所', '类型',
               '开通日期', '首班', '末班', '状态码', '扩展']

    h_font = Font(name='微软雅黑', bold=True, size=11, color='FFFFFF')
    h_fill = PatternFill(start_color='409EFF', end_color='409EFF', fill_type='solid')
    h_align = Alignment(horizontal='center', vertical='center', wrap_text=True)
    border = Border(left=Side(style='thin'), right=Side(style='thin'),
                    top=Side(style='thin'), bottom=Side(style='thin'))

    for col, h in enumerate(headers, 1):
        c = ws.cell(row=1, column=col, value=h)
        c.font = h_font
        c.fill = h_fill
        c.alignment = h_align
        c.border = border

    d_font = Font(name='微软雅黑', size=10)
    d_align = Alignment(horizontal='center', vertical='center')

    for ri, s in enumerate(stations, 2):
        vals = [s['country'], s['city'], s['stationName'], s['stationNameEn'], s['stationAlias'],
                s['longitude'], s['latitude'], s['isTransfer'], s['lineIds'], s['lineNames'],
                s['exitCount'], s['hasToilet'], s['stationType'], s['openDate'], s['firstTime'],
                s['lastTime'], s['statusCode'], s['extra']]
        for ci, v in enumerate(vals, 1):
            c = ws.cell(row=ri, column=ci, value=v)
            c.font = d_font
            c.alignment = d_align
            c.border = border

    for i, w in enumerate([8, 10, 18, 20, 18, 12, 12, 6, 10, 20,
                           8, 6, 6, 12, 8, 8, 8, 10], 1):
        ws.column_dimensions[get_column_letter(i)].width = w

    ws.freeze_panes = 'A2'
    ws.auto_filter.ref = f'A1:R{len(stations) + 1}'
    wb.save(output_path)


def crawl_city(city):
    """爬取单个城市，返回 (车站列表, 错误信息)"""
    elems, err = query_city(city)
    if err:
        time.sleep(REQUEST_INTERVAL)
        elems, err = query_city(city, relaxed=True)
    if err:
        return [], err
    return extract_stations(elems, city), None


def print_available_cities():
    """打印可用城市列表"""
    print(f'可用城市 ({len(CITIES)} 个):')
    for region, city_list in CITIES_BY_REGION:
        print(f'  [{region}] ' + '、'.join(city_list))


# ==================== 主流程 ====================

def main():
    print('=' * 60)
    print('  批量爬取地铁站数据')
    print(f'  开始时间: {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}')

    # --list 参数
    if '--list' in sys.argv or '-l' in sys.argv:
        print_available_cities()
        return

    # 解析要爬取的城市
    args = sys.argv[1:]
    if args:
        cities = []
        for name in args:
            name = name.strip()
            if name in CITIES:
                cities.append(name)
            else:
                print(f'⚠ 跳过未知城市: {name}')
        if not cities:
            print('❌ 未匹配到有效城市，使用 --list 查看可用城市')
            sys.exit(1)
    else:
        cities = CITIES

    print(f'  目标城市: {len(cities)} 个')
    if len(cities) <= 10:
        print(f'  → {", ".join(cities)}')
    print('=' * 60)

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    total_stations = 0
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')

    for idx, city in enumerate(cities, 1):
        print(f'\n[{idx:2d}/{len(cities)}] {city} ...', end=' ', flush=True)
        stations, err = crawl_city(city)

        if err:
            print(f'✗ {err}')
            continue

        if not stations:
            print('○ 无地铁站数据')
            continue

        # 每个城市保存独立文件
        filename = f'地铁站数据_{city}_{timestamp}.xlsx'
        out_path = os.path.join(OUTPUT_DIR, filename)
        save_to_excel(stations, out_path)
        total_stations += len(stations)
        print(f'✓ {len(stations)} 站 → {filename}')

        if idx < len(cities):
            time.sleep(REQUEST_INTERVAL)

    print(f'\n{"=" * 60}')
    print(f'✅ 批量爬取完成')
    print(f'   成功: {total_stations} 个站点')
    print(f'   文件: {OUTPUT_DIR}')
    print(f'   可直接在管理后台「地铁站管理→批量导入」上传各城市 Excel')
    print('=' * 60)


if __name__ == '__main__':
    main()
