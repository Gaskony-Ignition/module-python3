import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import PackagesView from './PackagesView'
import {
  mockGateway, defaultRoutes, callsTo, headersOf, textResponse, GATEWAY_PREFIX,
} from '../test/gatewayMock'

const GATEWAY_URL = window.location.origin + GATEWAY_PREFIX

describe('PackagesView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('renders the catalogue map with installed status', async () => {
    mockGateway(defaultRoutes())
    render(<PackagesView gatewayUrl={GATEWAY_URL} showToast={() => {}} />)

    expect(await screen.findByText('numpy')).toBeInTheDocument()
    expect(screen.getByText('pandas')).toBeInTheDocument()
    expect(screen.getByText('1 installed · 2 total')).toBeInTheDocument()
    expect(screen.getByTitle('Uninstall numpy')).toBeInTheDocument()
    expect(screen.getByTitle('Install pandas')).toBeInTheDocument()
  })

  it('shows the gateway error text and empty state when the catalogue fails', async () => {
    mockGateway({
      ...defaultRoutes(),
      '/api/v1/packages/catalog': () => textResponse('Package manager not initialised', 500),
    })
    render(<PackagesView gatewayUrl={GATEWAY_URL} showToast={() => {}} />)

    expect(await screen.findByText('Package manager not initialised')).toBeInTheDocument()
    expect(screen.getByText('No packages found.')).toBeInTheDocument()
  })

  it('filters installed packages and reports no match', async () => {
    mockGateway(defaultRoutes())
    render(<PackagesView gatewayUrl={GATEWAY_URL} showToast={() => {}} />)
    await screen.findByText('numpy')

    const search = screen.getByPlaceholderText('Search installed packages…')
    fireEvent.change(search, { target: { value: 'array' } })
    expect(screen.getByText('numpy')).toBeInTheDocument()
    expect(screen.queryByText('pandas')).not.toBeInTheDocument()

    fireEvent.change(search, { target: { value: 'scipy' } })
    expect(screen.getByText('No packages match "scipy"')).toBeInTheDocument()
  })

  it('Install POSTs to /packages/install/<name> with auth headers', async () => {
    const fetchMock = mockGateway({
      ...defaultRoutes(),
      '/api/v1/packages/install/': { success: true, message: 'Installed pandas' },
    })
    const showToast = vi.fn()
    render(<PackagesView gatewayUrl={GATEWAY_URL} showToast={showToast} />)

    fireEvent.click(await screen.findByTitle('Install pandas'))

    await waitFor(() => expect(showToast).toHaveBeenCalledWith('Package installed successfully', 'success'))
    const [call] = callsTo(fetchMock, '/api/v1/packages/install/pandas')
    expect(call[1]?.method).toBe('POST')
    expect(headersOf(call[1])).toMatchObject({
      Authorization: 'Bearer api-token-456',
      'X-CSRF-Token': 'csrf-token-123',
    })
  })
})
