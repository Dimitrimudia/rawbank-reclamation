# Module Réclamations Client (Fintech)

Ce module comprend:
- Frontend React (Vite) pour saisir une réclamation
- API Node/Express servant de proxy sécurisé vers Elastic
- Fichiers Elastic (pipeline + index + mapping)

## Prérequis
- Node.js 18+
- Accès à un cluster Elastic (URL + API Key ou utilisateur/mot de passe)

## Installation

```bash
# Frontend
cd ./frontend
npm install

# Backend
cd ../backend
cp .env.example .env
# Éditer .env pour ELASTIC_URL, ELASTIC_API_KEY (ou USERNAME/PASSWORD)
npm install
```

## Lancement en local

```bash
# Démarrer le backend (port 4000)
cd backend
npm run dev

# Démarrer le frontend (port 5173)
cd ../frontend
npm run dev
```

Vite est configuré pour proxyfier `/api` vers `http://localhost:4000`.

## Déploiement du pipeline et de l'index Elastic

Utilisez `curl` ou Kibana Dev Tools.

```bash
# Créer le pipeline d'ingest
curl -X PUT "$ELASTIC_URL/_ingest/pipeline/complaints_ingest_pipeline" \
  -H "Content-Type: application/json" \
  -H "Authorization: ApiKey $ELASTIC_API_KEY" \
  -d @elastic/complaints_pipeline.json

# Créer l'index (mapping + default_pipeline)
curl -X PUT "$ELASTIC_URL/complaints" \
  -H "Content-Type: application/json" \
  -H "Authorization: ApiKey $ELASTIC_API_KEY" \
  -d @elastic/complaints_index.json
```

> Alternative: si vous n'avez pas d'API Key, utilisez l'auth Basic:
>
> `-u "$ELASTIC_USERNAME:$ELASTIC_PASSWORD"` et supprimez l'en-tête `Authorization`.

## Sécurité backend
- CORS restreint à `FRONTEND_ORIGIN`
- Helmet pour les en-têtes de sécurité
- Limiteur de débit (100 req / 15 min)
- Validation stricte (Zod) côté serveur
- Clés/identifiants Elastic **jamais exposés au frontend**

## Visualisation (Kibana)
- Créez un **Data View** sur l'index `complaints`
- Dashboard de base:
  - Table: nombre de réclamations par `product`, `complaint_type`
  - Histogramme: réclamations par `submitted_at`
  - Filtre `status` = `new`/`resolved`

## Alerting recommandé
- Règle **Index Threshold**:
  - Index: `complaints`
  - Condition: `complaint_type` = `fraude` (via filtre KQL) ou terme dans `description` ("chargeback", "litige")
  - Seuil: count >= 1 sur fenêtre 5 min
  - Action: email/Slack vers équipe support
- Option avancée: Watcher (si licence): alerte sur pics anormaux (derivative sur count)

## Notes
- Le pipeline normalise `product` et `complaint_type` en minuscule et ajoute `ingested_at`.
- Le backend ajoute `status = new` par défaut.
- Le frontend est mobile-first et affiche le numéro de suivi (UUID v4).
