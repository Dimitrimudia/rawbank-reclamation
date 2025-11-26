import { useState } from 'react'
import { submitComplaint } from '../services/api.js'

export default function useSubmitComplaint() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const submit = async (payload) => {
    setLoading(true)
    setError(null)
    try {
      const res = await submitComplaint(payload)
      setLoading(false)
      if (!res.ok) {
        const msg = res.error || "Erreur lors de l'envoi"
        setError(msg)
        return { ok: false, error: msg }
      }
      return { ok: true, data: res.data }
    } catch (e) {
      setLoading(false)
      setError('Échec réseau ou serveur')
      return { ok: false, error: 'Échec réseau ou serveur' }
    }
  }

  return { submit, loading, error }
}
