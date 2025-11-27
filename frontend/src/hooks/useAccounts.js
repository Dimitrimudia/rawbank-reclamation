import { useEffect, useState } from 'react'
import { getAccounts } from '../services/api.js'

export default function useAccounts(clientId) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [accounts, setAccounts] = useState([])

  useEffect(() => {
    let active = true
    const digits = String(clientId || '').replace(/\D/g, '')
    if (digits.length !== 8) {
      setAccounts([])
      setError(null)
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)

    const attemptFetch = async (tries = 3, delayMs = 500) => {
      try {
        const res = await getAccounts(digits)
        if (!active) return
        if (res.ok) {
          setAccounts(res.accounts || [])
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
  }, [clientId])

  return { loading, error, accounts }
}
