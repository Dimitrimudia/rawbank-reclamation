# Infrastructure (Kafka, Elasticsearch, Kibana, External Mock)

This stack runs only external dependencies — NOT the backend/frontend.

## Prereqs
- Docker Desktop (macOS)
- Copy `.env.example` to `.env` and adjust secrets.

```sh
cp .env.example .env
```

## Start
```sh
docker compose up -d
```

Services:
- Kafka (KRaft, SASL/SCRAM) → `localhost:9092`
- Elasticsearch (security on, HTTP no TLS) → `http://localhost:9200`
- Kibana → `http://localhost:5601`
- External Accounts Mock (WireMock) → `http://localhost:9090`

The `es_setup` one-shot job will set the `kibana_system` password using `ELASTIC_PASSWORD`.

## Quick checks
```sh
# Kafka port
nc -z localhost 9092

# Elasticsearch health
curl -s -u elastic:$ELASTIC_PASSWORD http://localhost:9200 | jq .

# Kibana status
curl -s http://localhost:5601/api/status | jq .

# External mock
curl -s http://localhost:9090/health | jq .
```

## Notes
- For app clients: use Kafka SASL/SCRAM with `SCRAM-SHA-512` at `localhost:9092`.
- Elasticsearch uses basic auth. Kibana connects with `kibana_system` and the password you set.
- To add mock endpoints, drop WireMock files under `infra/accounts-mock/mappings` and `__files`.
