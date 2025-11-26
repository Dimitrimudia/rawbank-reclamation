import axios from 'axios'
import fs from 'fs'
import path from 'path'
import dotenv from 'dotenv'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

// Charge l'env du backend
dotenv.config({ path: path.resolve(__dirname, '..', '.env') })

const ELASTIC_URL = process.env.ELASTIC_URL
const ELASTIC_INDEX = process.env.ELASTIC_INDEX || 'complaints'
const ELASTIC_PIPELINE = process.env.ELASTIC_PIPELINE || 'complaints_ingest_pipeline'
const ELASTIC_API_KEY = process.env.ELASTIC_API_KEY
const ELASTIC_USERNAME = process.env.ELASTIC_USERNAME
const ELASTIC_PASSWORD = process.env.ELASTIC_PASSWORD

const elasticDir = path.resolve(__dirname, '../../elastic')
const pipelinePath = path.join(elasticDir, 'complaints_pipeline.json')
const indexPath = path.join(elasticDir, 'complaints_index.json')

function getHeaders() {
  const headers = { 'Content-Type': 'application/json' }
  if (ELASTIC_API_KEY) headers['Authorization'] = `ApiKey ${ELASTIC_API_KEY}`
  else if (ELASTIC_USERNAME && ELASTIC_PASSWORD) {
    const token = Buffer.from(`${ELASTIC_USERNAME}:${ELASTIC_PASSWORD}`).toString('base64')
    headers['Authorization'] = `Basic ${token}`
  }
  return headers
}

async function createPipeline() {
  const body = JSON.parse(fs.readFileSync(pipelinePath, 'utf-8'))
  const url = `${ELASTIC_URL}/_ingest/pipeline/${encodeURIComponent(ELASTIC_PIPELINE)}`
  const r = await axios.put(url, body, { headers: getHeaders() })
  console.log(`[OK] Pipeline '${ELASTIC_PIPELINE}' créé/mis à jour (${r.status})`)
}

async function indexExists() {
  try {
    const url = `${ELASTIC_URL}/${encodeURIComponent(ELASTIC_INDEX)}`
    await axios.head(url, { headers: getHeaders() })
    return true
  } catch (e) {
    if (e.response && e.response.status === 404) return false
    throw e
  }
}

async function createIndex() {
  const body = JSON.parse(fs.readFileSync(indexPath, 'utf-8'))
  const url = `${ELASTIC_URL}/${encodeURIComponent(ELASTIC_INDEX)}`
  const r = await axios.put(url, body, { headers: getHeaders() })
  console.log(`[OK] Index '${ELASTIC_INDEX}' créé (${r.status})`)
}

async function updateIndexSettingsDefaultPipeline() {
  const url = `${ELASTIC_URL}/${encodeURIComponent(ELASTIC_INDEX)}/_settings`
  const body = { index: { default_pipeline: ELASTIC_PIPELINE } }
  const r = await axios.put(url, body, { headers: getHeaders() })
  console.log(`[OK] Index settings mis à jour (default_pipeline=${ELASTIC_PIPELINE}) (${r.status})`)
}

async function updateIndexMapping() {
  const indexJson = JSON.parse(fs.readFileSync(indexPath, 'utf-8'))
  if (!indexJson.mappings) return console.log('[INFO] Pas de mappings à mettre à jour')
  const url = `${ELASTIC_URL}/${encodeURIComponent(ELASTIC_INDEX)}/_mapping`
  const r = await axios.put(url, indexJson.mappings, { headers: getHeaders() })
  console.log(`[OK] Mapping mis à jour (${r.status})`)
}

async function main() {
  if (!ELASTIC_URL) {
    console.error('ELASTIC_URL manquant. Renseignez backend/.env')
    process.exit(1)
  }
  if (!ELASTIC_API_KEY && !(ELASTIC_USERNAME && ELASTIC_PASSWORD)) {
    console.error('Auth Elastic manquante: ELASTIC_API_KEY ou ELASTIC_USERNAME/ELASTIC_PASSWORD')
    process.exit(1)
  }

  await createPipeline()
  if (await indexExists()) {
    console.log(`[INFO] Index '${ELASTIC_INDEX}' existe. Mise à jour settings + mapping…`)
    await updateIndexSettingsDefaultPipeline()
    await updateIndexMapping()
  } else {
    await createIndex()
  }

  console.log('[DONE] Setup Elastic terminé.')
}

main().catch((e) => {
  const status = e.response?.status
  const data = e.response?.data
  console.error('Erreur setup Elastic:', status || '', data || e.message)
  process.exit(1)
})
