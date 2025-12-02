import { useEffect, useState } from 'react'
import { getAccounts, getAccountsByPhone } from '../services/api.js'

// API unifiée: { mode: 'numeroClient' | 'telephoneClient', clientId, phone, enabled }
export default function useAccounts(params, enabledLegacy) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [accounts, setAccounts] = useState([])

  useEffect(() => {
    let active = true
    // Compatibilité ancienne signature: useAccounts(clientId, enabled)
    let mode, clientId, phone, enabled
    if (typeof params === 'string' || typeof params === 'number') {
      clientId = String(params || '')
      mode = 'numeroClient'
      enabled = enabledLegacy ?? true
    } else {
      mode = params?.mode
      clientId = params?.clientId
      phone = params?.phone
      enabled = params?.enabled ?? true
    }

    const clientDigits = String(clientId || '').replace(/\D/g, '')
    const phoneDigits = String(phone || '').replace(/\D/g, '')

    const shouldUseClient = mode === 'numeroClient' && clientDigits.length === 8
    const shouldUsePhone = mode === 'telephoneClient' && phoneDigits.length === 10
    if (!enabled || (!shouldUseClient && !shouldUsePhone)) {
      setAccounts([])
      setError(null)
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)

    const attemptFetch = async (tries = 3, delayMs = 500) => {
      try {
        const res = shouldUseClient
          ? await getAccounts(clientDigits)
          : await getAccountsByPhone(phoneDigits)
        if (!active) return
        if (res.ok) {
          // Normaliser en objets { id, label }
          const list = Array.isArray(res.accounts) ? res.accounts : []
          const normalized = list.map((v, idx) => {
            if (v && typeof v === 'object') {
              const id = v.id || v.number || v.iban || v.accountNumber || String(idx)
              const label = v.label || v.name || v.number || v.iban || id
              return { id, label }
            }
            const s = String(v ?? '')
            return { id: s, label: s }
          })
          setAccounts(normalized)
          setError(null)
          setLoading(false)
          return
        }
        // Erreur fonctionnelle: ne pas relancer, afficher l'erreur
        setAccounts([])
        setError(res.error || 'Erreur lors du chargement des comptes')
        setLoading(false)
      } catch (e) {
        if (!active) return
        if (tries > 1) {
          // Relance automatique en cas de problème de connexion (exponential backoff)
          setTimeout(() => attemptFetch(tries - 1, Math.min(delayMs * 3, 3000)), delayMs)
        } else {
          setError('Échec réseau ou serveur')
          setAccounts([])
          setLoading(false)
        }
      }
    }

    const t = setTimeout(() => attemptFetch(), 300) // debounce court

    return () => { active = false; clearTimeout(t) }
  }, [params, enabledLegacy])

  return { loading, error, accounts }
}
