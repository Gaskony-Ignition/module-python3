// Regression tests for web UI views that read gateway payloads under names the gateway
// never sends. Payloads are copied from a live 8.3.10 gateway running this module.
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import DashboardView from './DashboardView'
import VersionsView from './VersionsView'

const json = (body: unknown, status = 200) =>
  Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }))

function routeFetch(routes: Record<string, unknown>) {
  return vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    const hit = Object.keys(routes).find(k => url.endsWith(k))
    return hit ? json(routes[hit]) : json({ error: 'not found' }, 404)
  })
}

afterEach(() => vi.unstubAllGlobals())

describe('DashboardView', () => {
  it('shows the execution count /metrics reports in snake_case', async () => {
    vi.stubGlobal('fetch', routeFetch({
      '/api/v1/health': { healthy: true, available: true, status: 'HEALTHY' },
      '/api/v1/pool-stats': { totalSize: 3, available: 3, inUse: 0, healthy: 3 },
      '/api/v1/versions': { versions: ['3.11'], default: '3.11', count: 1 },
      '/api/v1/metrics': { total_executions: 42, successful_executions: 40, failed_executions: 2, average_execution_time_ms: 15 },
    }))
    render(<DashboardView gatewayUrl="/data/python3integration" onNavigate={() => {}} />)
    const label = await screen.findByText('Total Executions')
    await waitFor(() => expect(label.nextElementSibling).toHaveTextContent('42'))
  })

  it('says the gateway is unreachable when every request fails', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    render(<DashboardView gatewayUrl="/data/python3integration" onNavigate={() => {}} />)
    expect(await screen.findByText(/Unable to load gateway data/)).toBeInTheDocument()
  })
})

describe('VersionsView', () => {
  it('shows the install path, which only /versions carries', async () => {
    vi.stubGlobal('fetch', routeFetch({
      '/api/v1/distributions': { distributions: [
        { version: '3.11', fullVersion: '3.11.6', installed: false, available: true, poolActive: false },
        { version: '3.12', fullVersion: '3.12.1', installed: false, available: true, poolActive: false },
      ] },
      '/api/v1/versions': { versions: ['3.11'], default: '3.11', count: 1, details: {
        '3.11': { path: 'data/python3-integration/python/python/bin/python3.11', isDefault: true, poolSize: 3 },
      } },
      '/api/v1/pool-stats': { totalSize: 3, available: 3, inUse: 0, healthy: 3 },
    }))
    render(<VersionsView gatewayUrl="/data/python3integration" showToast={() => {}} />)
    expect(await screen.findByText('data/python3-integration/python/python/bin/python3.11')).toBeInTheDocument()
  })
})
