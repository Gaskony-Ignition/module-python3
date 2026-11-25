import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import VersionsView from './VersionsView'
import {
  mockGateway, defaultRoutes, callsTo, headersOf, jsonResponse, GATEWAY_PREFIX,
} from '../test/gatewayMock'

const GATEWAY_URL = window.location.origin + GATEWAY_PREFIX

describe('VersionsView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('lists installed and available distributions', async () => {
    mockGateway(defaultRoutes())
    render(<VersionsView gatewayUrl={GATEWAY_URL} showToast={() => {}} />)

    expect(await screen.findByText('Python 3.11')).toBeInTheDocument()
    expect(screen.getByText('Python 3.13')).toBeInTheDocument()
    expect(screen.getByTitle('Uninstall Python 3.11')).toBeInTheDocument()
    expect(screen.getByTitle('Install Python 3.13')).toBeInTheDocument()
  })

  it('shows the empty state when every endpoint answers 500', async () => {
    mockGateway({
      '/api/v1/': () => jsonResponse({ success: false, error: 'Internal error' }, 500),
    })
    render(<VersionsView gatewayUrl={GATEWAY_URL} showToast={() => {}} />)

    expect(await screen.findByText('No Python versions found.')).toBeInTheDocument()
    expect(screen.getByText('Click Refresh to reload or check gateway connectivity.')).toBeInTheDocument()
  })

  it('Install POSTs the version to /distributions/install with the CSRF token', async () => {
    const fetchMock = mockGateway({
      ...defaultRoutes(),
      '/api/v1/distributions/install': { success: true, message: 'Installed Python 3.13' },
    })
    const showToast = vi.fn()
    render(<VersionsView gatewayUrl={GATEWAY_URL} showToast={showToast} />)

    fireEvent.click(await screen.findByTitle('Install Python 3.13'))

    await waitFor(() =>
      expect(showToast).toHaveBeenCalledWith('Python version installed successfully', 'success')
    )
    const [call] = callsTo(fetchMock, '/api/v1/distributions/install')
    expect(call[1]?.method).toBe('POST')
    expect(JSON.parse(String(call[1]?.body))).toEqual({ version: '3.13' })
    expect(headersOf(call[1])['X-CSRF-Token']).toBe('csrf-token-123')
  })
})
