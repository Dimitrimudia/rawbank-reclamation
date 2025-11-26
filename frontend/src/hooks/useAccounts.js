import { useEffect, useState } from 'react'
import { getAccounts } from '../services/api.js'

export default function useAccounts(clientId) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [accounts, setAccounts] = useState([])

  useEffect(() => {
    let active = true
    if (!clientId || String(clientId).trim().length < 1) {
      setAccounts([])
      setError(null)
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)

    const t = setTimeout(async () => {
      try {
        const res = await getAccounts(clientId)
        if (!active) return
        if (res.ok) {
          setAccounts(res.accounts || [])
        } else {
          setError(res.error || 'Erreur lors du chargement des comptes')
          setAccounts([])
        }
      } catch (e) {
        if (!active) return
        setError('Échec réseau ou serveur')
        setAccounts([])
      } finally {
        if (active) setLoading(false)
      }
    }, 300) // debounce court

    return () => { active = false; clearTimeout(t) }
  }, [clientId])

  return { loading, error, accounts }
}
