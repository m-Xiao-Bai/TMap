#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
爬取脚本测试
============
测试 OSM Overpass API 连通性和数据格式。

用法:
  python 爬取脚本测试1.py

依赖:
  pip install requests
"""

import json
import requests


OVERPASS_URL = 'https://overpass-api.de/api/interpreter'

HEADERS = {
    'Accept': '*/*',
    'Accept-Language': 'zh-CN,zh;q=0.9',
    'Connection': 'keep-alive',
    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
    'Origin': 'https://overpass-turbo.eu',
    'Referer': 'https://overpass-turbo.eu/',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
}


def test_query(city='南宁市'):
    """测试查询"""
    query = f"""data=[out:json];
area[name='{city}']->.a;
(
  node['railway'='station']['station'='subway'](area.a);
  node['railway'='station']['station'='light_rail'](area.a);
  node['railway'='station']['station'='monorail'](area.a);
  way['railway'='station']['station'='subway'](area.a);
  way['railway'='station']['station'='light_rail'](area.a);
  way['railway'='station']['station'='monorail'](area.a);
);
out body;"""

    print(f'测试查询: {city}')
    print(f'URL: {OVERPASS_URL}')
    print(f'Query:\n{query}\n')

    try:
        resp = requests.post(OVERPASS_URL, headers=HEADERS, data={'data': query}, timeout=30)
        print(f'状态码: {resp.status_code}')

        if resp.status_code == 200:
            data = resp.json()
            elements = data.get('elements', [])
            print(f'返回元素数: {len(elements)}')

            if elements:
                print('\n前3个元素:')
                for elem in elements[:3]:
                    tags = elem.get('tags', {})
                    print(f'  - {tags.get("name:zh") or tags.get("name", "?")}')
                    print(f'    坐标: {elem.get("lat")}, {elem.get("lon")}')
                    print(f'    线路: {tags.get("line", "无")}')
                    print(f'    网络: {tags.get("network", "无")}')
            else:
                print('未找到元素')
        else:
            print(f'响应: {resp.text[:500]}')

    except Exception as e:
        print(f'错误: {e}')


def test_relaxed(city='南昌市'):
    """宽松查询"""
    query = f"""data=[out:json];
area[name='{city}']->.a;
(
  node['railway'='station']['subway'='yes'](area.a);
  node['railway'='station']['station'='subway'](area.a);
  node['railway'='station']['station'='light_rail'](area.a);
  way['railway'='station']['station'='subway'](area.a);
  way['railway'='station']['station'='light_rail'](area.a);
);
out body;"""

    print(f'\n宽松查询: {city}')

    try:
        resp = requests.post(OVERPASS_URL, headers=HEADERS, data={'data': query}, timeout=30)
        print(f'状态码: {resp.status_code}')

        if resp.status_code == 200:
            data = resp.json()
            elements = data.get('elements', [])
            print(f'返回元素数: {len(elements)}')

            if elements:
                print('\n前3个元素:')
                for elem in elements[:3]:
                    tags = elem.get('tags', {})
                    print(f'  - {tags.get("name:zh") or tags.get("name", "?")}')
                    print(f'    坐标: {elem.get("lat")}, {elem.get("lon")}')
        else:
            print(f'响应: {resp.text[:500]}')

    except Exception as e:
        print(f'错误: {e}')


if __name__ == '__main__':
    test_query()
    print('\n' + '='*50)
    test_relaxed()
