import { useState, useCallback } from 'react'
import { checkAuthResponse } from '../utils/authCheck'

/**
 * Fetch helper that handles Ignition's {data: ...} envelope unwrapping.
 * Returns a function that performs the fetch, plus loading/error state.
 *
 * Detects auth failures (HTML response from login redirect)
 * and returns a clear error message instead of a JSON parse error.
 */
export function useGatewayFetch(gatewayUrl: string) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async <T = unknown>(
    path: string,
    options?: RequestInit
  ): Promise<T | null> => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(`${gatewayUrl}/${path}`, options)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      // Detect auth failure (HTML login page instead of JSON)
      checkAuthResponse(res)
      const raw = await res.json()
      return (raw.data !== undefined ? raw.data : raw) as T
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Request failed'
      setError(msg)
      console.error(`[useGatewayFetch] ${path}:`, err)
      return null
    } finally {
      setLoading(false)
    }
  }, [gatewayUrl])

  return { fetchData, loading, error }
}
