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

for course in 1001 1002; do
  if [ "$course" = "1001" ]; then
    payload='{"id":1001,"name":"Java 微服务实战入门","categoryIdLv1":1,"categoryIdLv2":2,"categoryIdLv3":3,"free":false,"type":2,"sold":128,"price":19900,"score":45,"teacher":2,"sections":2,"mediaDuration":3600,"coverUrl":"","publishTime":"2026-09-01 10:00:00"}'
  else
    payload='{"id":1002,"name":"Spring Boot 快速入门（免费）","categoryIdLv1":1,"categoryIdLv2":2,"categoryIdLv3":3,"free":true,"type":2,"sold":256,"price":0,"score":48,"teacher":2,"sections":2,"mediaDuration":1800,"coverUrl":"","publishTime":"2026-09-02 10:00:00"}'
  fi
  curl -fsS -X PUT "$base_url/course/_doc/$course" \
    -H 'Content-Type: application/json' \
    -d "$payload" >/dev/null
done

curl -fsS -X POST "$base_url/course/_refresh" >/dev/null
echo "Elasticsearch course index is ready."
