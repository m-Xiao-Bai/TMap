import time

import requests
import json


headers = {
    'Accept': '*/*',
    'Accept-Language': 'zh-CN,zh;q=0.9',
    'Connection': 'keep-alive',
    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
    'Origin': 'https://overpass-turbo.eu',
    'Referer': 'https://overpass-turbo.eu/',
    'Sec-Fetch-Dest': 'empty',
    'Sec-Fetch-Mode': 'cors',
    'Sec-Fetch-Site': 'cross-site',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36',
    'sec-ch-ua': '"Google Chrome";v="143", "Chromium";v="143", "Not A(Brand";v="24"',
    'sec-ch-ua-mobile': '?0',
    'sec-ch-ua-platform': '"Windows"',
}
city_data =  [# 华北地区
     '北京市'
    '天津市',
    '石家庄市',
    '太原市',
    '呼和浩特市',
    # 东北地区
    '沈阳市',
    '大连市',
    '长春市',
    '哈尔滨市',
    # 华东地区
    '上海市',
    '南京市',
    '无锡市',
    '徐州市',
    '常州市',
    '苏州市',
    '杭州市',
    '宁波市',
    '温州市',
    '嘉兴市',
    '绍兴市',
    '合肥市',
    '福州市',
    '厦门市',
    '南昌市',
    '济南市',
    '青岛市',
    '淄博市',
    '烟台市',
    # 中南地区
    '郑州市',
    '武汉市',
    '长沙市',
    '广州市',
    '深圳市',
    '珠海市',
    '佛山市',
    '东莞市',
    '南宁市',
    '海口市',
    # 西南地区
    '重庆市',
    '成都市',
    '贵阳市',
    '昆明市',
    '拉萨市',
    # 西北地区
    '西安市',
    '兰州市',
    '西宁市',
    '银川市',
    '乌鲁木齐市',
    # 港澳台地区
    '香港',
    '澳门',
    '台北'
]

data_template = "data=[out:json];area[name='{city}']->.searchArea;(  node['railway'='station'](area.searchArea);  way['railway'='station'](area.searchArea);  relation['railway'='station'](area.searchArea););out body;"
city_err=[]
# 拼接数据
for city in city_data:
    try:
        data = data_template.format(city=city)
        response = requests.post('https://overpass-api.de/api/interpreter', headers=headers, data=data)
        if response.status_code == 200:
            # 3. 核心：将response.text（字符串）转为JSON对象
            json_data = json.loads(response.text)
            # 此时json_data是Python字典/列表，可正常操作（如取值、遍历）
            # DOT 处理爬取出来数据做excel表格,方便后面直接导入数据库
            time.sleep(3)
        else:
            print(f"请求失败{city}，状态码：{response.status_code}")
            city_err.append(city)
    except Exception as e:
        continue
print(city_err)