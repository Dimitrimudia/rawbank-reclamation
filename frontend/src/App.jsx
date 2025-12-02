import React, { useState } from 'react'
import ComplaintForm from './components/ComplaintForm.jsx'

export default function App() {
  const [submitted, setSubmitted] = useState(null)

  return (
    <div className="container">
      <header className="header">
        <div className="brand">
          <img
            className="brand-logo"
            src="/rawbank-logo.png"
            width="220"
            height="48"
            alt="Rawbank"
            loading="eager"
            decoding="async"
            fetchpriority="high"
          />
          <div className="brand-text">
            <h1 className="brand-title">Réclamations Client</h1>
          </div>
        </div>
      </header>

      {submitted && (
        <div className="success" role="status" aria-live="polite">
          <p style={{display:'flex',alignItems:'center',gap:8,margin:'0 0 6px'}}>
            <svg className="success-check" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
              <circle cx="12" cy="12" r="10" />
              <path d="M7 12.5l3.5 3.5L17 9" />
            </svg>
            <strong>Merci.</strong> Votre réclamation a été envoyée.
          </p>
          <div>Numéro de réclamation: <code>{submitted.complaintNumber}</code></div>
        </div>
      )}

      <ComplaintForm onSuccess={setSubmitted} />

      <footer className="footer">
        <small>&copy; {new Date().getFullYear()} Rawbank SA</small>
      </footer>
    </div>
  )
}
