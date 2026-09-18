# -*- coding: utf-8 -*-
"""
知序学堂 - ES 课程索引种子导入（Windows 下替代 elasticsearch-init.sh 的补充工具）
数据源：deploy/elasticsearch-init.sh 中的 COURSES 数据块（单一数据源，避免双份维护）
用途：sh 脚本在 Git Bash 下处理含中文的 heredoc 有编码问题，用本脚本导入更稳。

用法：
    python deploy/es_seed_courses.py [ES_URL]     # 默认 http://localhost:19200
"""
import json
import os
import re
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SH_PATH = os.path.join(ROOT, "deploy", "elasticsearch-init.sh")

MAPPING = {
    "settings": {"number_of_shards": 1, "number_of_replicas": 0},
    "mappings": {"properties": {
        "id": {"type": "long"},
        "name": {"type": "text"},
        "categoryIdLv1": {"type": "long"},
        "categoryIdLv2": {"type": "long"},
        "categoryIdLv3": {"type": "long"},
        "free": {"type": "boolean"},
        "type": {"type": "integer"},
        "sold": {"type": "integer"},
        "price": {"type": "integer"},
        "score": {"type": "integer"},
        "teacher": {"type": "long"},
        "sections": {"type": "integer"},
        "mediaDuration": {"type": "integer"},
        "coverUrl": {"type": "keyword"},
        "publishTime": {"type": "date",
                        "format": "yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis"},
    }},
}


def es(base, method, path, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        return resp.status, json.loads(resp.read().decode("utf-8"))


def parse_courses():
    """从 elasticsearch-init.sh 的 COURSES 数据块解析课程文档。"""
    text = open(SH_PATH, encoding="utf-8").read()
    block = re.search(r"<<'COURSES'\n(.*?)\nCOURSES", text, re.S).group(1)
    rows = []
    for line in block.splitlines():
        if not line.strip():
            continue
        cid, name, lv1, lv2, lv3, free, price, score, sold, duration, published = line.split("|")
        rows.append({
            "id": int(cid), "name": name, "categoryIdLv1": int(lv1),
            "categoryIdLv2": int(lv2), "categoryIdLv3": int(lv3),
            "free": free == "true", "type": 2, "sold": int(sold),
            "price": int(price), "score": int(score), "teacher": 2,
            "sections": 3, "mediaDuration": int(duration),
            "coverUrl": "/covers/%s.svg" % cid, "publishTime": published,
        })
    return rows


def main():
    base = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:19200").rstrip("/")
    # 等待 ES 就绪
    for _ in range(15):
        try:
            es(base, "GET", "/_cluster/health")
            break
        except Exception:
            time.sleep(2)
    # 建索引（已存在则跳过）
    try:
        es(base, "PUT", "/course", MAPPING)
        print("索引 course 已创建")
    except Exception as e:
        print("索引已存在或创建跳过:", str(e)[:80])
    rows = parse_courses()
    for r in rows:
        es(base, "PUT", "/course/_doc/%d" % r["id"], r)
    es(base, "POST", "/course/_refresh")
    _, cnt = es(base, "GET", "/course/_count")
    print("导入完成：脚本共 %d 门课程，ES 当前文档数 %d" % (len(rows), cnt["count"]))


if __name__ == "__main__":
    main()
