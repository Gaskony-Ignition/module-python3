/**
 * Centralized API client with CSRF token management.
 * All state-changing requests (POST, DELETE) include the X-CSRF-Token header.
 */
import { checkAuthResponse } from './authCheck'

const GATEWAY_URL = window.location.origin + '/data/python3integration'

let csrfToken: string | null = null
let apiToken: string | null = null
let tokenExpiry = 0

/**
 * Obtain a session/CSRF token and API Bearer token from the gateway.
 * Called once on app init and automatically refreshed before expiry.
 */
export async function initSession(): Promise<void> {
  try {
    const res = await fetch(`${GATEWAY_URL}/auth/session`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ client_id: 'gateway-web-ui' }),
      credentials: 'same-origin',
    })
    if (res.ok) {
      const raw = await res.json()
      const data = raw.data || raw
      if (data.success && data.token) {
        csrfToken = data.token
        apiToken = data.api_token || null
        // Refresh 60s before expiry (default expiry 8h)
        tokenExpiry = Date.now() + ((data.expires_in || 28800) - 60) * 1000
      }
    }
  } catch (e) {
    console.warn('[api] Failed to initialize session:', e)
  }
}

async function ensureToken(): Promise<void> {
  if (!csrfToken || Date.now() > tokenExpiry) {
    await initSession()
    // Retry once if first attempt failed (e.g. gateway was still starting)
    if (!csrfToken) {
      await new Promise(r => setTimeout(r, 1000))
      await initSession()
    }
  }
}

/** Build auth headers: Bearer token (for isGatewayAuthenticated) + CSRF token */
function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const h: Record<string, string> = { ...extra }
  if (apiToken) h['Authorization'] = `Bearer ${apiToken}`
  if (csrfToken) h['X-CSRF-Token'] = csrfToken
  return h
}

function unwrap<T>(raw: unknown): T {
  if (raw && typeof raw === 'object' && 'data' in raw) {
    return (raw as Record<string, unknown>).data as T
  }
  return raw as T
}

/** GET request (includes Bearer token for auth) */
export async function apiGet<T = unknown>(path: string, timeout = 10000): Promise<T> {
  await ensureToken()
  const res = await fetch(`${GATEWAY_URL}${path}`, {
    headers: authHeaders(),
    signal: AbortSignal.timeout(timeout),
    credentials: 'same-origin',
  })
  checkAuthResponse(res)
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(text || `HTTP ${res.status}`)
  }
  const raw = await res.json()
  return unwrap<T>(raw)
}

/** POST request (includes Bearer token + CSRF token) */
export async function apiPost<T = unknown>(path: string, body?: unknown, timeout = 60000): Promise<T> {
  await ensureToken()
  const res = await fetch(`${GATEWAY_URL}${path}`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: body !== undefined ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(timeout),
    credentials: 'same-origin',
  })
  checkAuthResponse(res)
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(text || `HTTP ${res.status}`)
  }
  const raw = await res.json()
  return unwrap<T>(raw)
}

/** DELETE request (includes Bearer token + CSRF token) */
export async function apiDelete<T = unknown>(path: string, timeout = 10000): Promise<T> {
  await ensureToken()
  const res = await fetch(`${GATEWAY_URL}${path}`, {
    method: 'DELETE',
    headers: authHeaders(),
    signal: AbortSignal.timeout(timeout),
    credentials: 'same-origin',
  })
  checkAuthResponse(res)
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(text || `HTTP ${res.status}`)
  }
  const raw = await res.json()
  return unwrap<T>(raw)
}

export function getGatewayUrl(): string {
  return GATEWAY_URL
}
