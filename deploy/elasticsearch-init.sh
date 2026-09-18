#!/bin/sh
set -eu

base_url="${ELASTICSEARCH_URL:-http://elasticsearch:9200}"

until curl -fsS "$base_url/_cluster/health?wait_for_status=yellow" >/dev/null; do
  sleep 2
done

index_status="$(curl -sS -o /dev/null -w '%{http_code}' "$base_url/course")"
if [ "$index_status" = "404" ]; then
  curl -fsS -X PUT "$base_url/course" \
    -H 'Content-Type: application/json' \
    -d '{
      "settings": {"number_of_shards": 1, "number_of_replicas": 0},
      "mappings": {
        "properties": {
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
          "publishTime": {"type": "date", "format": "yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis"}
        }
      }
    }'
fi

# 格式: id|name|lv1|lv2|lv3|free|price|score|sold|duration|published
# lv1 对应新分类体系一级分类：101=IT互联网 102=设计创意 103=办公职场 104=语言学习 105=考试认证
# PUT _doc 为幂等 upsert：索引已存在时也会刷新全部文档（可重复执行）
while IFS='|' read -r id name lv1 category2 category3 free price score sold duration published; do
  payload=$(cat <<EOF
{"id":$id,"name":"$name","categoryIdLv1":$lv1,"categoryIdLv2":$category2,"categoryIdLv3":$category3,"free":$free,"type":2,"sold":$sold,"price":$price,"score":$score,"teacher":2,"sections":3,"mediaDuration":$duration,"coverUrl":"/covers/$id.svg","publishTime":"$published"}
EOF
  )
  curl -fsS -X PUT "$base_url/course/_doc/$id" \
    -H 'Content-Type: application/json' \
    -d "$payload" >/dev/null
done <<'COURSES'
1001|Java 微服务实战入门|101|2|3|false|19900|45|128|3600|2026-09-01 10:00:00
1002|Spring Boot 快速入门（免费）|101|2|3|true|0|48|256|1800|2026-09-02 10:00:00
1003|Java 集合与并发编程|101|112|221|true|0|47|96|2400|2026-09-03 10:00:00
1004|Java 虚拟机性能调优|101|112|221|false|12900|46|72|3000|2026-09-04 10:00:00
1005|Java 设计模式实战|101|112|221|false|9900|49|88|2700|2026-09-05 10:00:00
1011|Spring Cloud 服务治理|101|4|5|false|15900|48|115|3300|2026-09-06 10:00:00
1012|Spring Cloud Alibaba 实战|101|4|5|false|19900|46|83|3600|2026-09-07 10:00:00
1013|微服务网关与容错|101|4|5|true|0|45|141|2100|2026-09-08 10:00:00
1021|MySQL 从入门到索引优化|101|6|7|true|0|48|203|3000|2026-09-09 10:00:00
1022|MySQL 事务与锁机制|101|6|7|false|11900|47|67|2700|2026-09-10 10:00:00
1023|MySQL 高可用架构|101|6|7|false|17900|46|54|3300|2026-09-11 10:00:00
1031|Redis 数据结构精讲|101|8|9|true|0|49|192|2400|2026-09-12 10:00:00
1032|Redis 缓存与分布式锁|101|8|9|false|10900|48|116|2700|2026-09-13 10:00:00
1033|Redis 集群与高并发|101|8|9|false|16900|47|79|3300|2026-09-14 10:00:00
1041|Python 编程基础|101|112|222|true|0|49|177|2400|2026-09-15 10:00:00
1042|Python 数据处理入门|101|112|222|false|9900|47|69|2700|2026-09-16 10:00:00
1043|Python Web 开发实战|101|112|222|false|14900|46|61|3300|2026-09-17 10:00:00
2101|操作系统原理与实践入门|101|111|211|false|8900|46|58|3000|2026-09-18 10:00:00
2102|计算机网络：从 TCP/IP 到 HTTP|101|111|212|true|0|47|120|2700|2026-09-18 10:00:00
2103|数据结构与链表实战|101|111|213|false|9900|48|75|2400|2026-09-18 10:00:00
2104|算法入门：排序与查找|101|111|214|true|0|49|140|2700|2026-09-18 10:00:00
2211|Java 21 新特性实战|101|112|221|false|12900|47|64|2700|2026-09-19 10:00:00
2212|Python 自动化脚本实战|101|112|222|false|9900|46|82|2400|2026-09-19 10:00:00
2213|Go 语言并发编程入门|101|112|223|true|0|48|96|3000|2026-09-19 10:00:00
2411|UI 设计基础：从临摹到原创|102|141|241|true|0|47|88|2700|2026-09-20 10:00:00
2412|Figma 高效设计工作流|102|141|242|false|11900|48|70|3000|2026-09-20 10:00:00
2413|界面设计规范与组件库搭建|102|141|243|false|13900|46|52|3300|2026-09-20 10:00:00
2414|交互设计入门：用户旅程与原型|102|141|244|false|10900|47|63|2700|2026-09-20 10:00:00
2511|AI 绘画：Stable Diffusion 实战|102|142|251|false|14900|48|105|3300|2026-09-21 10:00:00
2512|AI 设计工具全指南|102|142|252|false|9900|46|58|2700|2026-09-21 10:00:00
2513|Prompt 工程入门与实践|102|142|253|true|0|49|167|2400|2026-09-21 10:00:00
2514|AI 工作流：让重复工作自动化|102|142|254|false|12900|47|71|3000|2026-09-21 10:00:00
2611|Word 长文档排版实战|103|161|261|false|6900|47|66|2100|2026-09-22 10:00:00
2612|Excel 函数与数据透视表|103|161|262|true|0|49|230|3000|2026-09-22 10:00:00
2613|PPT 演示设计进阶|103|161|263|false|7900|46|54|2400|2026-09-22 10:00:00
2614|WPS 办公全家桶速成|103|161|264|false|5900|45|40|1800|2026-09-22 10:00:00
2711|时间管理：告别拖延症|103|162|271|true|0|48|145|1800|2026-09-23 10:00:00
2712|任务管理：GTD 实践指南|103|162|272|false|6900|46|49|2100|2026-09-23 10:00:00
2713|知识管理：搭建个人第二大脑|103|162|273|false|8900|47|62|2400|2026-09-23 10:00:00
2714|AI 办公效率革命|103|162|274|false|9900|48|85|2400|2026-09-23 10:00:00
2811|英语基础：音标与核心语法|104|181|281|true|0|47|110|3000|2026-09-24 10:00:00
2812|英语听力专项训练|104|181|282|false|7900|46|57|2700|2026-09-24 10:00:00
2813|英语口语：日常对话突破|104|181|283|false|8900|47|63|2700|2026-09-24 10:00:00
2814|商务英语：职场沟通实战|104|181|284|false|11900|48|75|3000|2026-09-24 10:00:00
2911|CET-4 词汇与真题精讲|105|191|291|true|0|48|188|3300|2026-09-25 10:00:00
2912|CET-6 听力阅读冲刺|105|191|292|false|9900|46|66|2700|2026-09-25 10:00:00
2913|计算机等级考试一级通关|105|191|293|false|6900|47|58|2400|2026-09-25 10:00:00
2914|软考中级：系统集成项目管理|105|192|294|false|15900|47|73|3600|2026-09-25 10:00:00
2915|云计算基础与上云实践|105|192|295|false|13900|46|51|3300|2026-09-25 10:00:00
COURSES

curl -fsS -X POST "$base_url/course/_refresh" >/dev/null
echo "Elasticsearch course index is ready."
