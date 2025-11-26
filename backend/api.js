import express from 'express'
import helmet from 'helmet'
import cors from 'cors'
import morgan from 'morgan'
import rateLimit from 'express-rate-limit'
import axios from 'axios'
import { z } from 'zod'
import dotenv from 'dotenv'

dotenv.config()

const PORT = process.env.PORT || 4000
const FRONTEND_ORIGIN = process.env.FRONTEND_ORIGIN || 'http://localhost:5173'
const ELASTIC_URL = process.env.ELASTIC_URL
const ELASTIC_INDEX = process.env.ELASTIC_INDEX || 'complaints'
const ELASTIC_PIPELINE = process.env.ELASTIC_PIPELINE || 'complaints_ingest_pipeline'
const ELASTIC_API_KEY = process.env.ELASTIC_API_KEY
const ELASTIC_USERNAME = process.env.ELASTIC_USERNAME
const ELASTIC_PASSWORD = process.env.ELASTIC_PASSWORD

if (!ELASTIC_URL) {
  console.error('ELASTIC_URL manquant dans l\'environnement')
}

const app = express()
app.use(helmet({ crossOriginResourcePolicy: false }))
app.use(express.json({ limit: '100kb' }))
app.use(cors({ origin: FRONTEND_ORIGIN, methods: ['POST'], credentials: false }))
app.use(morgan('combined'))

const limiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 100 })
app.use('/api/', limiter)

const bodySchema = z.object({
  tracking_id: z.string().uuid(),
  name: z.string().min(2),
  email: z.string().email(),
  phone: z.string().min(6),
  product: z.string().min(2),
  complaint_type: z.string().min(2),
  description: z.string().min(10),
  user_agent: z.string().min(1),
  device: z.enum(['mobile', 'desktop']),
  submitted_at: z.string().datetime(),
  submitted_at_local: z.string().min(4)
}).passthrough()

app.post('/api/complaints', async (req, res) => {
  const parsed = bodySchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Payload invalide', details: parsed.error.issues })
  }

  const doc = {
    ...parsed.data,
    status: 'new'
  }

  try {
    const url = `${ELASTIC_URL}/${encodeURIComponent(ELASTIC_INDEX)}/_doc?pipeline=${encodeURIComponent(ELASTIC_PIPELINE)}`
    const headers = { 'Content-Type': 'application/json' }

    if (ELASTIC_API_KEY) {
      headers['Authorization'] = `ApiKey ${ELASTIC_API_KEY}`
    } else if (ELASTIC_USERNAME && ELASTIC_PASSWORD) {
      const token = Buffer.from(`${ELASTIC_USERNAME}:${ELASTIC_PASSWORD}`).toString('base64')
      headers['Authorization'] = `Basic ${token}`
    } else {
      console.warn('Aucune méthode d\'auth fournie pour Elastic (API key ou basic).')
    }

    const r = await axios.post(url, doc, { headers, timeout: 5000 })
    if (r.status >= 200 && r.status < 300) {
      return res.status(201).json({ trackingId: doc.tracking_id })
    }
    return res.status(502).json({ error: 'Elastic a rejeté la requête' })
  } catch (e) {
    const status = e.response?.status || 500
    const msg = e.response?.data || e.message
    return res.status(status >= 400 && status < 600 ? status : 500).json({ error: 'Erreur backend', details: msg })
  }
})

app.get('/health', (_req, res) => res.json({ ok: true }))

app.listen(PORT, () => {
  console.log(`API réclamations en écoute sur http://localhost:${PORT}`)
})
