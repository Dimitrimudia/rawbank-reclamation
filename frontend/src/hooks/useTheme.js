import { useEffect, useState } from 'react'

const STORAGE_KEY = 'theme-preference' // 'light' | 'dark' | 'system'

function getSystemTheme() {
  if (typeof window === 'undefined') return 'light'
  return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export default function useTheme() {
  const [mode, setMode] = useState(() => localStorage.getItem(STORAGE_KEY) || 'system')
  const effective = mode === 'system' ? getSystemTheme() : mode

  useEffect(() => {
    const root = document.documentElement
    root.setAttribute('data-theme', effective)
  }, [effective])

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, mode)
  }, [mode])

  const toggle = () => {
    setMode((m) => (m === 'light' ? 'dark' : m === 'dark' ? 'system' : 'light'))
  }

  return { mode, effective, setMode, toggle }
}
