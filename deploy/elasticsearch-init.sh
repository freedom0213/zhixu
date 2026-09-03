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

while IFS='|' read -r id name category2 category3 free price score sold duration published; do
  payload=$(cat <<EOF
{"id":$id,"name":"$name","categoryIdLv1":1,"categoryIdLv2":$category2,"categoryIdLv3":$category3,"free":$free,"type":2,"sold":$sold,"price":$price,"score":$score,"teacher":2,"sections":3,"mediaDuration":$duration,"coverUrl":"","publishTime":"$published"}
EOF
  )
  curl -fsS -X PUT "$base_url/course/_doc/$id" \
    -H 'Content-Type: application/json' \
    -d "$payload" >/dev/null
done <<'COURSES'
1001|Java 微服务实战入门|2|3|false|19900|45|128|3600|2026-09-01 10:00:00
1002|Spring Boot 快速入门（免费）|2|3|true|0|48|256|1800|2026-09-02 10:00:00
1003|Java 集合与并发编程|2|12|true|0|47|96|2400|2026-09-03 10:00:00
1004|Java 虚拟机性能调优|2|12|false|12900|46|72|3000|2026-09-04 10:00:00
1005|Java 设计模式实战|2|12|false|9900|49|88|2700|2026-09-05 10:00:00
1011|Spring Cloud 服务治理|4|5|false|15900|48|115|3300|2026-09-06 10:00:00
1012|Spring Cloud Alibaba 实战|4|5|false|19900|46|83|3600|2026-09-07 10:00:00
1013|微服务网关与容错|4|5|true|0|45|141|2100|2026-09-08 10:00:00
1021|MySQL 从入门到索引优化|6|7|true|0|48|203|3000|2026-09-09 10:00:00
1022|MySQL 事务与锁机制|6|7|false|11900|47|67|2700|2026-09-10 10:00:00
1023|MySQL 高可用架构|6|7|false|17900|46|54|3300|2026-09-11 10:00:00
1031|Redis 数据结构精讲|8|9|true|0|49|192|2400|2026-09-12 10:00:00
1032|Redis 缓存与分布式锁|8|9|false|10900|48|116|2700|2026-09-13 10:00:00
1033|Redis 集群与高并发|8|9|false|16900|47|79|3300|2026-09-14 10:00:00
1041|Python 编程基础|10|11|true|0|49|177|2400|2026-09-15 10:00:00
1042|Python 数据处理入门|10|11|false|9900|47|69|2700|2026-09-16 10:00:00
1043|Python Web 开发实战|10|11|false|14900|46|61|3300|2026-09-17 10:00:00
COURSES

curl -fsS -X POST "$base_url/course/_refresh" >/dev/null
echo "Elasticsearch course index is ready."
