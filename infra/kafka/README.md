# Kafka Sécurisé (SASL_SSL) — Dev

Ce dossier contient:
- `generate-certs.sh`: script pour générer des certificats auto-signés (CA + keystore/truststore) à usage DEV.
- `ssl/`: keystore / truststore montés dans le container `kafka`.

## Génération rapide
```bash
chmod +x infra/kafka/generate-certs.sh
./infra/kafka/generate-certs.sh
```
Variables personnalisables avant exécution:
```bash
export KEYSTORE_PASS='FortKeystore!2025'
export TRUSTSTORE_PASS='FortTrust!2025'
export KEY_PASS='FortKey!2025'
export CN='rawbank-kafka.local'
./infra/kafka/generate-certs.sh
```

## Mise en place docker-compose
Le `docker-compose.yml` monte `./infra/kafka/ssl` dans `/etc/kafka/ssl` et active le listener `SASL_SSL://0.0.0.0:9094`.
Définir dans `.env` ou export shell:
```
KAFKA_SSL_KEYSTORE_PASSWORD=FortKeystore!2025
KAFKA_SSL_KEY_PASSWORD=FortKey!2025
KAFKA_SSL_TRUSTSTORE_PASSWORD=FortTrust!2025
SPRING_KAFKA_SECURITY_PROTOCOL=SASL_SSL
SPRING_KAFKA_SASL_MECHANISM=SCRAM-SHA-512
SPRING_KAFKA_USERNAME=app
SPRING_KAFKA_PASSWORD=<motdepassefort>
SPRING_KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.scram.ScramLoginModule required username="app" password="<motdepassefort>";
SPRING_KAFKA_SSL_TRUSTSTORE_LOCATION=./infra/kafka/ssl/kafka.truststore.jks
SPRING_KAFKA_SSL_TRUSTSTORE_PASSWORD=FortTrust!2025
```

## Création de l’utilisateur SCRAM
Le service `kafka-init` tente de créer l’utilisateur `app` automatiquement. Si besoin manuel:
```bash
docker exec -it rawbank-kafka bash
kafka-configs --bootstrap-server localhost:9092 --alter \
  --add-config 'SCRAM-SHA-512=[password=<motdepassefort>]' \
  --entity-type users --entity-name app
```

## Test de connectivité
```bash
docker compose up -d
./mvnw -q spring-boot:run
```

## Attention
Ces certificats sont auto-signés (non sûrs pour PROD). Utiliser une AC interne ou publique en production, et envisager la rotation régulière des secrets SCRAM.
