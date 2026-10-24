export interface BrowserSession { token: string; clientId: string }
const SESSION_KEY = 'nexus-session'
let connecting: Promise<BrowserSession> | undefined

export function ensureSession(): Promise<BrowserSession> {
  try {
    const saved = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null')
    if (typeof saved?.token === 'string' && typeof saved?.clientId === 'string') {
      return Promise.resolve({ token: saved.token, clientId: saved.clientId })
    }
  } catch { /* Ignore an invalid cached session. */ }
  if (!connecting) {
    connecting = (async () => {
      const response = await fetch('/api/v1/auth/connect', { method: 'POST' })
      if (!response.ok) throw new Error('Unable to connect your session. Please retry.')
      const result = await response.json()
      if (!result.token || !result.clientId) throw new Error('Unable to connect your session. Please retry.')
      const session = { token: result.token, clientId: result.clientId }
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
      return session
    })().finally(() => { connecting = undefined })
  }
  return connecting
}
