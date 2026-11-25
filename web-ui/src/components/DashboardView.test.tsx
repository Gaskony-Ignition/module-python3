import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import DashboardView from './DashboardView'
import { mockGateway, defaultRoutes, callsTo, GATEWAY_PREFIX } from '../test/gatewayMock'

const GATEWAY_URL = window.location.origin + GATEWAY_PREFIX

describe('DashboardView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('shows health, pool and version cards from the gateway', async () => {
    mockGateway(defaultRoutes())
    render(<DashboardView gatewayUrl={GATEWAY_URL} onNavigate={() => {}} />)

    expect(await screen.findByText('Healthy')).toBeInTheDocument()
    expect(screen.getByText('Process Pool')).toBeInTheDocument()
    expect(screen.getByTitle('33% utilization')).toBeInTheDocument() // 1 in use of 3
    expect(screen.getByText('3.11')).toBeInTheDocument()
    expect(screen.getByText(/^Last updated /)).toBeInTheDocument()
  })

  it('shows Down and the empty versions message when the gateway is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    render(<DashboardView gatewayUrl={GATEWAY_URL} onNavigate={() => {}} />)

    expect(await screen.findByText('Down')).toBeInTheDocument()
    expect(screen.getByText('No versions detected')).toBeInTheDocument()
  })

  it('refetches every card when Refresh is pressed', async () => {
    const fetchMock = mockGateway(defaultRoutes())
    render(<DashboardView gatewayUrl={GATEWAY_URL} onNavigate={() => {}} />)
    await screen.findByText('Healthy')
    expect(callsTo(fetchMock, '/api/v1/health')).toHaveLength(1)

    fireEvent.click(screen.getByRole('button', { name: 'Refresh dashboard' }))
    await waitFor(() => expect(callsTo(fetchMock, '/api/v1/health')).toHaveLength(2))
    expect(callsTo(fetchMock, '/api/v1/pool-stats')).toHaveLength(2)
  })
})
