import React, { useMemo, useState, useEffect } from 'react'
import { z } from 'zod'
import { v4 as uuidv4 } from 'uuid'
import useSubmitComplaint from '../hooks/useSubmitComplaint.js'
import useAccounts from '../hooks/useAccounts.js'

const numberOrEmpty = z.preprocess((val) => {
  if (val === '' || val === undefined || val === null) return undefined
  const raw = typeof val === 'string' ? val : String(val)
  // Nettoyage: supprime espaces (y compris insécables) et points de milliers, puis virgule -> point
  const normalized = raw
    .replace(/[\s\u00A0\u202F]/g, '')
    .replace(/\./g, '')
    .replace(',', '.')
  const n = Number(normalized)
  return Number.isFinite(n) ? n : undefined
}, z.number().positive().optional())

const schema = z.object({
  typeReclamation: z.string().min(2, 'Type requis'),
  description: z.string().min(10, 'Description trop courte'),
  domaine: z.string().optional().or(z.literal('')),
  numeroClient: z.string().optional().or(z.literal('')),
  telephoneClient: z.string().optional().or(z.literal('')),
  numeroCarte: z.string().optional().or(z.literal('')),
  dateTransaction: z.string().optional().or(z.literal('')),
  compteSource: z.string().optional().or(z.literal('')),
  montant: numberOrEmpty,
  devise: z.string().optional(),
  // montantConverti supprimé
  motif: z.string().optional().or(z.literal('')),
  extourne: z.boolean().optional(),
}).superRefine((data, ctx) => {
  if (data.extourne) {
    if (!data.montant || Number.isNaN(data.montant)) {
      ctx.addIssue({ code: 'custom', path: ['montant'], message: 'Montant requis si remboursé' })
    }
    if (!data.devise) {
      ctx.addIssue({ code: 'custom', path: ['devise'], message: 'Devise requise si remboursé' })
    }
    if (!data.compteSource) {
      ctx.addIssue({ code: 'custom', path: ['compteSource'], message: 'Compte source requis si remboursé' })
    }
  }
  if (data.domaine === 'Monetique' && !data.numeroCarte) {
    ctx.addIssue({ code: 'custom', path: ['numeroCarte'], message: 'Numéro carte requis pour domaine Monétique' })
  }
})

const types = ['Fraude', 'Chargeback', 'Service', 'Technique']
const currencies = ['USD', 'EUR', 'CDF']
const domainOptions = ['Monetique', 'Cartes', 'Comptes', 'Digital', 'Fraude', 'Transfert', 'Change', 'Support']
// const channels = ['RAWBOT', 'Agence', 'App', 'Web'] // non utilisé

function detectDevice(ua) {
  return /Mobile|Android|iPhone|iPad/i.test(ua) ? 'mobile' : 'desktop'
}

export default function ComplaintForm({ onSuccess }) {
  const { submit, loading, error } = useSubmitComplaint()
  const [apiError, setApiError] = useState(null)
  useEffect(() => { setApiError(error || null) }, [error])
  useEffect(() => {
    if (!apiError) return
    const t = setTimeout(() => setApiError(null), 30000)
    return () => clearTimeout(t)
  }, [apiError])
  const parseAmount = (v) => {
    if (v === '' || v === undefined || v === null) return undefined
    const normalized = String(v)
      .replace(/[\s\u00A0\u202F]/g, '')
      .replace(/\./g, '')
      .replace(',', '.')
    const n = Number(normalized)
    return Number.isFinite(n) ? n : undefined
  }
  const [form, setForm] = useState({
    typeReclamation: types[0],
    description: '',
    // Champs additionnels
    domaine: '',
    // canalUtilise retiré
    numeroClient: '',
    telephoneClient: '',
    numeroCarte: '',
    dateTransaction: (() => { const d = new Date(); const y = d.getFullYear(); const m = String(d.getMonth()+1).padStart(2,'0'); const day = String(d.getDate()).padStart(2,'0'); return `${y}-${m}-${day}` })(),
    compteSource: '',
    montant: '',
    devise: currencies[0],
    // montantConverti supprimé
    motif: '',
    // avisMotive retiré,
      // motifBcc retiré,
    extourne: false,
    
  })
  const [errors, setErrors] = useState({})
  const { loading: loadingAccounts, error: accountsError, accounts } = useAccounts(form.numeroClient)

  const userAgent = useMemo(() => navigator.userAgent, [])
  const device = useMemo(() => detectDevice(navigator.userAgent), [])

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target
    let v
    if (apiError) setApiError(null)
    if (type === 'checkbox') {
      v = checked
    } else if (type === 'radio' && name === 'extourne') {
      v = value === 'oui'
    } else if (name === 'montant') {
      const cleaned = String(value).replace(/[^0-9.,]/g, '')
      v = cleaned
    } else if (name === 'numeroCarte') {
      const digits = String(value).replace(/\D/g, '')
      v = digits.replace(/(.{4})/g, '$1 ').trim()
    } else {
      v = value
    }
    setForm((f) => ({ ...f, [name]: v }))
  }
  const handleBlur = (e) => {
    const { name } = e.target
    if (name === 'montant') {
      const val = String(form.montant || '').replace(',', '.')
      if (val && !Number.isNaN(Number(val))) {
        const formatted = Number(val).toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
        setForm((f) => ({ ...f, montant: formatted }))
      }
    }
  }
  const handleClear = () => {
    setForm({
      typeReclamation: types[0],
      description: '',
      domaine: '',
      // canalUtilise retiré
      numeroClient: '',
      telephoneClient: '',
      numeroCarte: '',
      dateTransaction: (() => { const d = new Date(); const y = d.getFullYear(); const m = String(d.getMonth()+1).padStart(2,'0'); const day = String(d.getDate()).padStart(2,'0'); return `${y}-${m}-${day}` })(),
      compteSource: '',
      montant: '',
      devise: currencies[0],
      // montantConverti supprimé
      motif: '',
      // avisMotive retiré,
      // motifBcc retiré,
      extourne: false,
      
    })
    setErrors({})
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    const parse = schema.safeParse(form)
    if (!parse.success) {
      const fieldErrors = {}
      parse.error.issues.forEach((i) => { fieldErrors[i.path[0]] = i.message })
      setErrors(fieldErrors)
      return
    }
    setErrors({})

    const trackingId = uuidv4()
    const now = new Date()

    const payload = {
      tracking_id: trackingId,
      description: form.description,
      user_agent: userAgent,
      device,
      submitted_at: now.toISOString(),
      submitted_at_local: now.toLocaleString('fr-FR'),
      // Champs métier (clés comme dans le JSON fourni)
      ...(form.domaine ? { DOMAINE: form.domaine } : {}),
      // CANALUTILISE retiré du payload
      ...(form.numeroClient ? { NUMEROCLIENT: form.numeroClient } : {}),
      ...(form.telephoneClient ? { TELEPHONECLIENT: form.telephoneClient } : {}),
      ...(form.numeroCarte ? { NUMEROCARTE: String(form.numeroCarte).replace(/\s+/g, '') } : {}),
      ...(form.dateTransaction ? { DATETRANSACTION: form.dateTransaction } : {}),
      ...(form.compteSource ? { COMPTESOURCE: form.compteSource } : {}),
      ...(parseAmount(form.montant) !== undefined ? { MONTANT: parseAmount(form.montant) } : {}),
      ...(form.devise ? { DEVISE: form.devise } : {}),
      // MONTANTCONVERTI supprimé
      ...(form.motif ? { MOTIF: form.motif } : {}),
      // AVISMOTIVE retiré,
      // MOTIFBCC retiré du payload,
      ...(typeof form.extourne === 'boolean' ? { EXTOURNE: form.extourne } : {}),
      // Alignement business
      TYPERECLAMATION: form.typeReclamation,
      DESCRIPTION: form.description
    }

    const res = await submit(payload)
    if (res?.ok) {
      onSuccess?.({ trackingId })
      setForm({
        typeReclamation: types[0],
        description: '',
        domaine: '',
        // canalUtilise retiré
        numeroClient: '',
        telephoneClient: '',
        numeroCarte: '',
        dateTransaction: (() => { const d = new Date(); const y = d.getFullYear(); const m = String(d.getMonth()+1).padStart(2,'0'); const day = String(d.getDate()).padStart(2,'0'); return `${y}-${m}-${day}` })(),
        compteSource: '',
        montant: '',
        devise: currencies[0],
        // montantConverti supprimé
        motif: '',
        // avisMotive retiré,
        // motifBcc retiré,
        extourne: false,
        
      })
    }
  }

  return (
    <>
      {apiError && (
        <div className="error box prominent dismissible" role="alert">
          <span className="error-text">{apiError}</span>
          <button
            type="button"
            className="error-close"
            aria-label="Fermer l’alerte"
            onClick={() => setApiError(null)}
          >
            ×
          </button>
        </div>
      )}
      <form className="card" onSubmit={handleSubmit} noValidate aria-busy={loading}>
        {loading && (
          <div className="card-loading-overlay" aria-hidden="true">
            <div className="loading-row">
              <svg className="spinner spinner-lg" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <circle cx="12" cy="12" r="10" stroke="rgba(238,116,68,.3)" strokeWidth="4"/>
                <path d="M22 12a10 10 0 0 1-10 10" stroke="var(--primary-600)" strokeWidth="4"/>
              </svg>
              <span>Envoi en cours…</span>
            </div>
          </div>
        )}
      <div className="card-header">
        <div className="card-header-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M12 2a5 5 0 0 1 5 5v1h1a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h1V7a5 5 0 0 1 5-5Zm0 2a3 3 0 0 0-3 3v1h6V7a3 3 0 0 0-3-3Z" fill="currentColor"/></svg>
        </div>
        <div className="card-header-text">
          <h2 className="card-title">Soumettre une réclamation</h2>
          <p className="card-subtitle">Vos informations sont traitées de façon sécurisée.</p>
        </div>
      </div>

      <div className="form-layout">
        <div className="form-main">
          <h3 className="section-title"><span className="badge">1</span> Détails de la réclamation</h3>
          <div className="grid" role="group" aria-label="Détails réclamation">
              <div className="field floating">
                <div className="control">
                  <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M4 4h16v2H4V4Zm0 6h16v2H4v-2Zm0 6h10v2H4v-2Z" fill="currentColor"/></svg>
                  <select name="domaine" id="domaine" value={form.domaine} onChange={handleChange}>
                    {domainOptions.map((d) => (<option key={d} value={d}>{d}</option>))}
                  </select>
                  <span className="floating-label" id="label-domaine">Domaine</span>
                </div>
              </div>

              <div className="field floating">
                <div className="control">
                  <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M12 2a10 10 0 1 0 10 10A10.011 10.011 0 0 0 12 2Zm1 15h-2v-2h2Zm0-4h-2V7h2Z" fill="currentColor"/></svg>
                  <select name="typeReclamation" value={form.typeReclamation} onChange={handleChange} aria-required="true" className={errors.typeReclamation ? 'invalid shake' : ''}>
                    {types.map((t) => (<option key={t} value={t}>{t}</option>))}
                  </select>
                  <span className="floating-label" id="label-typeReclamation">Type de réclamation*</span>
                </div>
                {errors.typeReclamation && <span className="error" role="alert">{errors.typeReclamation}</span>}
              </div>

              {/* Champ canal retiré */}
              <fieldset className="field" aria-label="Remboursement">
                <legend className="label">Remboursement</legend>
                <div className="control" role="radiogroup" aria-labelledby="remboursement-label">
                  <span id="remboursement-label" className="sr-only">Remboursement</span>
                  <label className={`radio-pill ${form.extourne === true ? 'active' : ''}`}>
                    <input type="radio" name="extourne" value="oui" checked={form.extourne === true} onChange={handleChange} />
                    <span>Oui</span>
                  </label>
                  <label className={`radio-pill ${form.extourne === false ? 'active' : ''}`}>
                    <input type="radio" name="extourne" value="non" checked={form.extourne === false} onChange={handleChange} />
                    <span>Non</span>
                  </label>
                </div>
              </fieldset>
              {form.extourne && (
                <>
                  <div className="field floating">
                    <div className="control">
                      <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M12 2a10 10 0 1 0 10 10A10.011 10.011 0 0 0 12 2Zm1 15h-2v-2h2Zm0-4h-2V7h2Z" fill="currentColor"/></svg>
                      <input name="montant" id="montant" inputMode="decimal" value={form.montant} onChange={handleChange} onBlur={handleBlur} placeholder=" " />
                      <span className="floating-label" id="label-montant">Montant*</span>
                    </div>
                    {errors.montant && <span className="error" role="alert">{errors.montant}</span>}
                    {!errors.montant && <small className="helper">Ex: 12,50</small>}
                  </div>
                  <div className="field floating">
                    <div className="control">
                      <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2Zm1 15h-2v-2H9v-2h2V9h2v2h2v2h-2v2Z" fill="currentColor"/></svg>
                      <select name="devise" value={form.devise} onChange={handleChange}>
                        {currencies.map((c) => (<option key={c} value={c}>{c}</option>))}
                      </select>
                      <span className="floating-label" id="label-devise">Devise*</span>
                    </div>
                    {errors.devise && <span className="error" role="alert">{errors.devise}</span>}
                    {!errors.devise && <small className="helper">Choisissez la monnaie</small>}
                  </div>
                  <div className="field floating">
                    <div className="control">
                      <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M3 7h18v2H3V7Zm0 4h18v2H3v-2Zm0 4h12v2H3v-2Z" fill="currentColor"/></svg>
                      {accounts && accounts.length > 0 ? (
                        <select name="compteSource" id="compteSource" value={form.compteSource} onChange={handleChange}>
                          <option value="">Sélectionnez un compte</option>
                          {accounts.map((acc, idx) => {
                            const id = acc.id || acc.number || acc.iban || acc.accountNumber || String(idx)
                            const label = acc.label || acc.name || acc.number || acc.iban || id
                            return <option key={id} value={id}>{label}</option>
                          })}
                        </select>
                      ) : (
                        <input name="compteSource" id="compteSource" value={form.compteSource} onChange={handleChange} placeholder=" " />
                      )}
                      <span className="floating-label" id="label-compteSource">Compte source*</span>
                    </div>
                    {errors.compteSource && <span className="error" role="alert">{errors.compteSource}</span>}
                    {!errors.compteSource && (
                      <small className="helper">
                        {loadingAccounts ? 'Chargement des comptes…' : accounts && accounts.length > 0 ? 'Choisissez un compte' : 'Numéro de compte/IBAN interne'}
                      </small>
                    )}
                    {accountsError && !errors.compteSource && (
                      <small className="error" role="alert">{accountsError}</small>
                    )}
                  </div>
                </>
              )}
          </div>

          <h3 className="section-title"><span className="badge">2</span> Informations complémentaires</h3>
          <div id="extras-section">
            <div className="grid" role="group" aria-label="Informations complémentaires">

        {/* Champ canal retiré */}

        

        <div className="field floating">
          <div className="control">
            <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M4 4h16v16H4V4Zm4 4h8v2H8V8Z" fill="currentColor"/></svg>
            <input name="numeroClient" id="numeroClient" value={form.numeroClient} onChange={handleChange} placeholder=" " />
            <span className="floating-label" id="label-numeroClient">Numéro client</span>
          </div>
        </div>

        <div className="field floating">
          <div className="control">
            <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2a1 1 0 0 1 1.05-.24 11.36 11.36 0 0 0 3.58.57 1 1 0 0 1 1 1v3.6a1 1 0 0 1-1 1A17 17 0 0 1 3 7a1 1 0 0 1 1-1h3.6a1 1 0 0 1 1 1 11.36 11.36 0 0 0 .57 3.58 1 1 0 0 1-.24 1.05l-2.31 2.16Z" fill="currentColor"/></svg>
            <input name="telephoneClient" id="telephoneClient" value={form.telephoneClient} onChange={handleChange} placeholder=" " />
            <span className="floating-label" id="label-telephoneClient">Téléphone client</span>
          </div>
        </div>

        {form.domaine === 'Monetique' && (
          <div className="field floating">
            <div className="control">
              <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M2 7a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7Zm2 0h16v2H4V7Z" fill="currentColor"/></svg>
              <input name="numeroCarte" id="numeroCarte" value={form.numeroCarte} onChange={handleChange} placeholder=" " />
              <span className="floating-label" id="label-numeroCarte">Numéro carte*</span>
            </div>
            {errors.numeroCarte && <span className="error" role="alert">{errors.numeroCarte}</span>}
          </div>
        )}

        <div className="field floating">
          <div className="control">
            <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M7 2v2H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-2V2h-2v2H9V2H7Zm12 8H5v8h14v-8Z" fill="currentColor"/></svg>
            <input type="date" name="dateTransaction" value={form.dateTransaction} onChange={handleChange} />
            <span className="floating-label" id="label-dateTransaction">Date transaction</span>
          </div>
        </div>

        

        <div className="field floating full-row">
          <div className="control">
            <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M4 3h12l4 4v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm2 4v2h12V7H6Zm0 4v2h12v-2H6Zm0 4v2h8v-2H6Z" fill="currentColor"/></svg>
            <input name="motif" id="motif" value={form.motif} onChange={handleChange} placeholder=" " />
            <span className="floating-label" id="label-motif">Motif</span>
          </div>
        </div>

        {/* Champ Avis motivé retiré */}

        {/* Champ Motif BCC retiré – bloc supprimé pour éviter tout texte résiduel */}

        
            </div>
          </div>

          {/* Déplacement de la Description dans la section 2 */}
          <div className="grid" role="group" aria-label="Description">
            <div className="field floating field-textarea full-row">
              <div className="control">
                <svg className="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M4 3h12l4 4v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm2 4v2h12V7H6Zm0 4v2h12v-2H6Zm0 4v2h8v-2H6Z" fill="currentColor"/></svg>
                <textarea name="description" id="description" value={form.description} onChange={handleChange} placeholder=" " rows={6} aria-required="true" className={errors.description ? 'invalid shake' : ''} />
                <span className="floating-label" id="label-description">Description*</span>
              </div>
              <small className="helper">Donnez les détails utiles pour accélérer le traitement</small>
              {errors.description && <span className="error" role="alert">{errors.description}</span>}
            </div>
          </div>
        </div>
      </div>
      <p className="note">En soumettant, vous acceptez que vos données soient utilisées pour traiter votre réclamation.</p>
      <div className="btn-row">
        <button className="btn" type="submit" disabled={loading} aria-busy={loading}>
          {loading ? (
            <>
              <svg className="spinner" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="12" cy="12" r="10" stroke="rgba(255,255,255,.4)" strokeWidth="4"/><path d="M22 12a10 10 0 0 1-10 10" stroke="#fff" strokeWidth="4"/></svg>
              Envoi…
            </>
          ) : 'Envoyer la réclamation'}
        </button>
        <button type="button" className="btn btn-secondary" onClick={handleClear} disabled={loading}>Effacer</button>
      </div>
      </form>
    </>
  )
}
