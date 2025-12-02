#!/usr/bin/env bash
set -euo pipefail

# Génération auto-signée pour environnement DEV: CA, keystore broker, truststore broker & client.
# ATTENTION: Ne pas utiliser en production. Remplacer par des certificats émis par une AC interne.
# Fichiers générés dans infra/kafka/ssl:
#  - ca.crt (certificat CA)
#  - kafka.keystore.jks (clé privée + cert signé)
#  - kafka.truststore.jks (contient le CA)
#  - client.truststore.jks (optionnel, même CA pour clients externes)
# Variables personnalisables via env avant exécution:
#  KEYSTORE_PASS, TRUSTSTORE_PASS, KEY_PASS, CN

SSL_DIR="$(dirname "$0")/ssl"
mkdir -p "$SSL_DIR"
cd "$SSL_DIR"

KEYSTORE_PASS="${KEYSTORE_PASS:-ChangeItKeystore123!}"
TRUSTSTORE_PASS="${TRUSTSTORE_PASS:-ChangeItTrust123!}"
KEY_PASS="${KEY_PASS:-ChangeItKey123!}"
CN="${CN:-rawbank-kafka.dev.local}"

echo "[i] Génération CA..."
openssl req -x509 -new -nodes -sha256 -days 365 -newkey rsa:4096 \
  -subj "/CN=$CN/O=RawBank/OU=Reclamations/L=Kinshasa/C=CD" \
  -keyout ca.key -out ca.crt >/dev/null 2>&1

# Créer un keystore PKCS12 initial
echo "[i] Génération clé privée et CSR du broker..."
keytool -genkeypair -alias kafka-broker -keyalg RSA -keysize 4096 -validity 365 \
  -keystore kafka.keystore.p12 -storetype PKCS12 \
  -storepass "$KEYSTORE_PASS" -keypass "$KEY_PASS" \
  -dname "CN=$CN, OU=Reclamations, O=RawBank, L=Kinshasa, C=CD"

keytool -certreq -alias kafka-broker -keystore kafka.keystore.p12 -storepass "$KEYSTORE_PASS" -file broker.csr

echo "[i] Signature du certificat broker avec CA..."
openssl x509 -req -in broker.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out broker.crt -days 365 -sha256 >/dev/null 2>&1

# Import CA & cert signé dans le keystore
keytool -importcert -noprompt -alias CAROOT -file ca.crt -keystore kafka.keystore.p12 -storepass "$KEYSTORE_PASS"
keytool -importcert -noprompt -alias kafka-broker -file broker.crt -keystore kafka.keystore.p12 -storepass "$KEYSTORE_PASS"

# Convertir PKCS12 vers JKS pour compatibilité plus large
keytool -importkeystore -srckeystore kafka.keystore.p12 -srcstoretype PKCS12 -srcstorepass "$KEYSTORE_PASS" \
  -destkeystore kafka.keystore.jks -deststoretype JKS -deststorepass "$KEYSTORE_PASS" -destkeypass "$KEY_PASS"

# Création truststore
keytool -importcert -noprompt -alias CAROOT -file ca.crt -keystore kafka.truststore.jks -storepass "$TRUSTSTORE_PASS"
cp kafka.truststore.jks client.truststore.jks

# Nettoyage fichiers temporaires
rm -f broker.csr broker.crt kafka.keystore.p12 ca.key ca.srl || true

echo "[✔] Certificats générés:"
ls -1

echo "\nVariables à définir dans .env / export pour Spring et docker-compose:"\n
echo "KAFKA_SSL_KEYSTORE_PASSWORD=$KEYSTORE_PASS"
echo "KAFKA_SSL_KEY_PASSWORD=$KEY_PASS"
echo "KAFKA_SSL_TRUSTSTORE_PASSWORD=$TRUSTSTORE_PASS"
echo "SPRING_KAFKA_SSL_TRUSTSTORE_LOCATION=$(pwd)/kafka.truststore.jks"
echo "SPRING_KAFKA_SSL_TRUSTSTORE_PASSWORD=$TRUSTSTORE_PASS"