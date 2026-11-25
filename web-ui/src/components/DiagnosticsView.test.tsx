import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import DiagnosticsView from './DiagnosticsView'
import {
  mockGateway, defaultRoutes, callsTo, jsonResponse, GATEWAY_PREFIX,
} from '../test/gatewayMock'

const GATEWAY_URL = window.location.origin + GATEWAY_PREFIX

describe('DiagnosticsView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('shows pool stats, health and module log entries', async () => {
    mockGateway(defaultRoutes())
    render(<DiagnosticsView gatewayUrl={GATEWAY_URL} />)

    expect(screen.getByRole('heading', { name: 'Diagnostics' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Pool size 3, click to change' })).toBeInTheDocument()
    expect(await screen.findByText('Executor 2 slow to respond')).toBeInTheDocument()
    expect(screen.getByText('2 entries (module only)')).toBeInTheDocument()
  })

  it('shows each panel’s empty message when the gateway answers 500', async () => {
    mockGateway({ '/api/v1/': () => jsonResponse({ success: false, error: 'Internal error' }, 500) })
    render(<DiagnosticsView gatewayUrl={GATEWAY_URL} />)

    expect(await screen.findByText('No pool data available.')).toBeInTheDocument()
    expect(screen.getByText('No metrics data available.')).toBeInTheDocument()
    expect(screen.getByText('No log entries found')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Raw Diagnostics/ }))
    expect(screen.getByText('(no diagnostics data)')).toBeInTheDocument()
  })

  it('requests only ERROR logs when the ERROR filter is chosen', async () => {
    const fetchMock = mockGateway(defaultRoutes())
    render(<DiagnosticsView gatewayUrl={GATEWAY_URL} />)
    await screen.findByText('Executor 2 slow to respond')

    fireEvent.click(screen.getByRole('button', { name: 'ERROR' }))

    await waitFor(() => {
      const levels = callsTo(fetchMock, '/api/v1/logs').map(([u]) => new URL(String(u)).searchParams.get('level'))
      expect(levels).toContain('ERROR')
    })
    const logCalls = callsTo(fetchMock, '/api/v1/logs')
    const last = logCalls[logCalls.length - 1]
    const params = new URL(String(last[0])).searchParams
    expect(params.get('filter')).toBe('Python3')
    expect(params.get('lines')).toBe('100')
  })

  it('validates the pool size and POSTs a valid resize to /pool-size', async () => {
    const fetchMock = mockGateway({
      ...defaultRoutes(),
      '/api/v1/pool-size': { success: true, poolSize: 5, message: 'Pool size changed to 5' },
    })
    render(<DiagnosticsView gatewayUrl={GATEWAY_URL} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Pool size 3, click to change' }))
    const input = screen.getByRole('spinbutton')
    fireEvent.change(input, { target: { value: '25' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(await screen.findByText('Must be 1–20')).toBeInTheDocument()
    expect(callsTo(fetchMock, '/api/v1/pool-size')).toHaveLength(0)

    fireEvent.change(input, { target: { value: '5' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    await waitFor(() => expect(callsTo(fetchMock, '/api/v1/pool-size')).toHaveLength(1))
    const [call] = callsTo(fetchMock, '/api/v1/pool-size')
    expect(call[1]?.method).toBe('POST')
    expect(JSON.parse(String(call[1]?.body))).toEqual({ size: 5 })
  })
})
