# TLS – Kafka (KRaft) et Elasticsearch/Kibana

Ce guide fournit des étapes reproductibles pour activer TLS côté Docker Compose.

## 1) Pré-requis
- OpenSSL installé (macOS: `brew install openssl`)
- Docker / Docker Compose

## 2) Kafka – Générer JKS (serveur + truststore)
Créez un dossier de certificats:

```bash
mkdir -p certs/kafka
cd certs/kafka
```

Générez un keystore et un certificat auto-signé pour `kafka` (CN= kafka):

```bash
keytool -genkeypair -alias kafka -keyalg RSA -keysize 4096 -validity 3650 \
  -keystore kafka.keystore.jks -storepass changeit -keypass changeit \
  -dname "CN=kafka, OU=Dev, O=Org, L=City, S=State, C=FR"

keytool -exportcert -alias kafka -keystore kafka.keystore.jks -storepass changeit -rfc -file kafka.crt
keytool -importcert -alias kafka -file kafka.crt -keystore kafka.truststore.jks -storepass changeit -noprompt
```

Revenez à la racine du projet et lancez avec l’override TLS:

```bash
docker compose -f docker-compose.yml -f docker-compose.kafka-ssl.yml up -d
```

Côté application Spring, configurez le truststore si nécessaire (client strict):

- Dans `backend-spring/.env` (exemple):

```env
SPRING_KAFKA_SECURITY_PROTOCOL=SASL_SSL
SPRING_KAFKA_SASL_MECHANISM=SCRAM-SHA-512
SPRING_KAFKA_USERNAME=app
SPRING_KAFKA_PASSWORD=app-secret
# Chemins locaux (adapter) et mot de passe
SPRING_KAFKA_SSL_TRUSTSTORE_LOCATION=certs/kafka/kafka.truststore.jks
SPRING_KAFKA_SSL_TRUSTSTORE_PASSWORD=changeit
```

Et dans `application.properties` si vous activez TLS client strict:

```properties
spring.kafka.properties.ssl.truststore.location=${SPRING_KAFKA_SSL_TRUSTSTORE_LOCATION:}
spring.kafka.properties.ssl.truststore.password=${SPRING_KAFKA_SSL_TRUSTSTORE_PASSWORD:}
```

## 3) Elasticsearch – Générer `http.p12`
Créez un dossier de certificats:

```bash
mkdir -p certs/elasticsearch
cd certs/elasticsearch
```

Générez un certificat P12 pour HTTPS (via openssl + PKCS#12):

```bash
openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 -nodes \
  -keyout http.key -out http.crt -subj "/CN=elasticsearch/OU=Dev/O=Org/L=City/S=State/C=FR"

# Packager en PKCS#12 (mot de passe: changeit)
openssl pkcs12 -export -in http.crt -inkey http.key -out http.p12 -name http -passout pass:changeit
```

(Revenez à la racine du projet) Lancez avec l’override HTTPS:

```bash
docker compose -f docker-compose.yml -f docker-compose.es-ssl.yml up -d
```

Kibana est configuré pour se connecter en HTTPS via l’utilisateur `kibana_system` (non-superuser). Le service `es_setup` définira le mot de passe via l’API sécurité.

## 4) Variables d’environnement
- Racine `.env` (utilisé par docker-compose):
  - `ELASTIC_PASSWORD` (superuser interne elastic – initialisation exclusivement)
  - `KIBANA_SYSTEM_PASSWORD` (mot de passe du compte technique)
  - `KAFKA_USERNAME`/`KAFKA_PASSWORD` (SASL/SCRAM)

- `backend-spring/.env`: variables Spring Kafka/ES pour le client.

## 5) Notes
- Pour prod: utilisez une AC interne, certificats nominatifs (SAN: `DNS:kafka`, `DNS:localhost` si besoin), et TLS bidirectionnel si requis.
- Vous pouvez activer `xpack.security.http.ssl.enabled=true` uniquement avec des certificats valides en place.
