export async function submitComplaint(data) {
  const res = await fetch('/api/complaints', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) {
    let message = 'Erreur API'
    try { const j = await res.json(); message = j?.error || message } catch {}
    return { ok: false, error: message }
  }
  const json = await res.json()
  return { ok: true, data: json }
}

export async function getAccounts(clientId) {
  const url = `/api/accounts/details?clientId=${encodeURIComponent(clientId)}`
  const res = await fetch(url, { method: 'GET' })
  if (!res.ok) {
    let message = 'Erreur API comptes'
    try { const j = await res.json(); message = j?.error || message } catch {}
    return { ok: false, error: message }
  }
  const json = await res.json()
  if (json?.ok) return { ok: true, accounts: (json.details || json.accounts || []) }
  return { ok: false, error: json?.error || 'Réponse invalide' }
}
