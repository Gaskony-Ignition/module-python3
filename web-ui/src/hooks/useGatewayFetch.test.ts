import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useGatewayFetch } from './useGatewayFetch'
import { mockGateway, jsonResponse, GATEWAY_PREFIX, healthPayload } from '../test/gatewayMock'

const BASE = window.location.origin + GATEWAY_PREFIX

describe('useGatewayFetch', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('returns the unwrapped payload and clears loading', async () => {
    mockGateway({ '/api/v1/health': { data: healthPayload } })
    const { result } = renderHook(() => useGatewayFetch(BASE))
    let data: unknown
    await act(async () => {
      data = await result.current.fetchData('api/v1/health')
    })
    expect(data).toEqual(healthPayload)
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('reports HTTP failures as an error and returns null', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    mockGateway({ '/api/v1/health': () => jsonResponse({ success: false, error: 'boom' }, 500) })
    const { result } = renderHook(() => useGatewayFetch(BASE))
    let data: unknown = 'unset'
    await act(async () => {
      data = await result.current.fetchData('api/v1/health')
    })
    expect(data).toBeNull()
    expect(result.current.error).toBe('HTTP 500')
  })
})
