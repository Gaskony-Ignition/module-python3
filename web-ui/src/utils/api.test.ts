import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mockGateway, callsTo, headersOf, sessionPayload, textResponse } from '../test/gatewayMock'

// api.ts caches the session token at module level, so each test gets a fresh copy.
async function loadApi() {
  vi.resetModules()
  return import('./api')
}

describe('api client', () => {
  beforeEach(() => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('creates a session, then apiPost sends its Bearer and CSRF tokens with a JSON body', async () => {
    const fetchMock = mockGateway({
      '/auth/session': sessionPayload,
      '/api/v1/exec': { success: true, result: '2' },
    })
    const api = await loadApi()
    const data = await api.apiPost<{ success: boolean; result: string }>('/api/v1/exec', { code: 'print(1+1)' })

    expect(data).toEqual({ success: true, result: '2' })
    const [session] = callsTo(fetchMock, '/auth/session')
    expect(session[1]?.method).toBe('POST')
    expect(JSON.parse(String(session[1]?.body))).toEqual({ client_id: 'gateway-web-ui' })
    const [call] = callsTo(fetchMock, '/api/v1/exec')
    expect(call[1]?.method).toBe('POST')
    expect(headersOf(call[1])).toMatchObject({
      'Content-Type': 'application/json',
      Authorization: 'Bearer api-token-456',
      'X-CSRF-Token': 'csrf-token-123',
    })
    expect(JSON.parse(String(call[1]?.body))).toEqual({ code: 'print(1+1)' })
  })

  it('apiGet unwraps the Ignition {data: ...} envelope', async () => {
    mockGateway({
      '/auth/session': { data: sessionPayload },
      '/api/v1/health': { data: { healthy: true, status: 'HEALTHY' } },
    })
    const api = await loadApi()
    await expect(api.apiGet('/api/v1/health')).resolves.toEqual({ healthy: true, status: 'HEALTHY' })
  })

  it('throws the server error body when the gateway answers 500', async () => {
    mockGateway({
      '/auth/session': sessionPayload,
      '/api/v1/packages/catalog': () => textResponse('Python 3 process pool is not initialized', 500),
    })
    const api = await loadApi()
    await expect(api.apiGet('/api/v1/packages/catalog')).rejects.toThrow(
      'Python 3 process pool is not initialized'
    )
  })

  it('throws AuthRequiredError when a request is redirected to the login page', async () => {
    mockGateway({
      '/auth/session': sessionPayload,
      '/api/v1/health': () => textResponse('<html></html>', 200, 'text/html'),
    })
    const api = await loadApi()
    await expect(api.apiGet('/api/v1/health')).rejects.toMatchObject({ name: 'AuthRequiredError' })
  })
})
