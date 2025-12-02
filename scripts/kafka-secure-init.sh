#!/usr/bin/env bash
set -euo pipefail

# Kafka secure init: create SCRAM user and topic from inside the broker container.
# Usage: ./scripts/kafka-secure-init.sh [broker_container_name] [bootstrap] [username] [password] [topic]
# Defaults:
#   broker_container_name: rawbank-kafka
#   bootstrap: kafka:9092   (PLAINTEXT admin path)
#   username: ${KAFKA_USERNAME:-app}
#   password: ${KAFKA_PASSWORD:-app-secret}
#   topic: complaints_raw

container_name=${1:-rawbank-kafka}
bootstrap=${2:-kafka:9092}
username=${3:-${KAFKA_USERNAME:-app}}
password=${4:-${KAFKA_PASSWORD:-app-secret}}
topic=${5:-complaints_raw}

echo "[secure-init] waiting for broker ${bootstrap}..."
until docker exec -i "${container_name}" kafka-broker-api-versions --bootstrap-server "${bootstrap}" >/dev/null 2>&1; do
  echo '[secure-init] broker not ready'; sleep 2;
done

echo "[secure-init] creating SCRAM user '${username}'"
docker exec -i "${container_name}" kafka-configs --bootstrap-server "${bootstrap}" \
  --alter --add-config "SCRAM-SHA-512=[password=${password}]" \
  --entity-type users --entity-name "${username}" || true

echo "[secure-init] ensuring topic '${topic}' exists"
docker exec -i "${container_name}" kafka-topics --bootstrap-server "${bootstrap}" \
  --create --if-not-exists --topic "${topic}" --replication-factor 1 --partitions 3 || true

echo "[secure-init] done"
