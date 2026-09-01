#!/bin/sh
set -eu

base_url="http://nacos:8848/nacos/v1/cs/configs"

for file in /configs/*.yaml; do
  [ -f "$file" ] || continue
  data_id=$(basename "$file")
  echo "Importing $data_id"
  curl -fsS -X POST "$base_url" \
    --data-urlencode "dataId=$data_id" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content=$(cat "$file")"
  echo
done

echo "Nacos local configuration imported."
