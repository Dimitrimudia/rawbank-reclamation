# Backend Spring Boot — Réclamations

Backend Java Spring Boot exposant `POST /api/complaints` (construction serveur d’un payload complet à partir d’un JSON par défaut, soumission vers Power Automate + indexation Elasticsearch optionnelle) et `GET/POST /api/accounts` (liste des comptes via API protégée par JWT).

## Configuration

Variables d'environnement (ou `application.yml`):

- `PORT` (def: 8080)
- `ALLOWED_ORIGINS` (def: `http://localhost:5173,http://localhost:3000`)
- `ELASTICSEARCH_URL` (def: `http://localhost:9200`)
- `ELASTICSEARCH_INDEX` (def: `reclamations`)
- `ELASTICSEARCH_USERNAME` (optionnel)
- `ELASTICSEARCH_PASSWORD` (optionnel)

Power Automate:
- `POWER_AUTOMATE_URL` (requis pour activer l'envoi vers Power Automate)
- `POWER_AUTOMATE_APIKEY_HEADER_NAME` (optionnel, ex: `x-api-key`)
- `POWER_AUTOMATE_APIKEY` (optionnel)

API Comptes (OAuth2 client_credentials):
- `ACCOUNTS_TOKEN_URL` (URL du token endpoint)
- `ACCOUNTS_CLIENT_ID`
- `ACCOUNTS_CLIENT_SECRET`
- `ACCOUNTS_SCOPE` (optionnel)
- `ACCOUNTS_API_URL` (endpoint de l'API comptes, ex: `https://api.rawbank/comptes`)
- `ACCOUNTS_METHOD` (POST|GET, défaut: POST)

## Démarrer en local

```bash
cd backend-spring
./mvnw spring-boot:run
```

Sur Windows:
```bat
mvnw.cmd spring-boot:run
```

L'API sera disponible sur `http://localhost:8080` (routes principales ci-dessous).

Routes:
- `POST /api/complaints` — côté serveur, construit un payload final en fusionnant un JSON par défaut (`default-payload.json`) avec les données entrantes (normalisations de `NUMEROCARTE`, `MONTANT`, etc.), soumet vers Power Automate; indexe ensuite dans Elasticsearch (si configuré). Réponse: `{ ok: true, powerAutomate: {...}, elasticsearch?: {...} }` ou `{ ok: false, error }`.
- `POST /api/accounts` — corps JSON `{ "clientId": "..." }`; récupère la liste des comptes. Réponse: `{ ok: true, accounts: [...] }` ou `{ ok: false, error }`.
- `GET /api/accounts?clientId=...` — alternative maintenue pour compatibilité, selon vos besoins.

## CORS

Les origines `http://localhost:5173` et `http://localhost:3000` sont autorisées par défaut. Ajustez `ALLOWED_ORIGINS` si besoin.

## Réponse
### Payload par défaut

Le fichier `src/main/resources/default-payload.json` contient tous les champs attendus par le processus Power Automate. À l'envoi, le backend fusionne ce JSON avec les valeurs de la requête (celles-ci priment) et applique quelques normalisations (carte sans espaces, montant numérisé, etc.).
- Succès: `201` `{ ok: true, result: { _index, _id, ... } }`
- Erreur: `400` `{ ok: false, error: "Payload invalide ou erreur serveur" }`
