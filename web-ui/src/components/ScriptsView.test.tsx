import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import ScriptsView from './ScriptsView'
import {
  mockGateway, defaultRoutes, callsTo, jsonResponse, GATEWAY_PREFIX,
} from '../test/gatewayMock'

const GATEWAY_URL = window.location.origin + GATEWAY_PREFIX

describe('ScriptsView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('lists saved scripts and their folders', async () => {
    mockGateway(defaultRoutes())
    render(<ScriptsView gatewayUrl={GATEWAY_URL} showToast={() => {}} />)

    expect(screen.getByRole('heading', { name: 'Scripts' })).toBeInTheDocument()
    expect(await screen.findByText('TankLevels')).toBeInTheDocument()
    expect(screen.getByText('Housekeeping')).toBeInTheDocument()
    expect(screen.getByText('Reports')).toBeInTheDocument()
  })

  it('shows the empty message when no scripts are saved', async () => {
    mockGateway({ ...defaultRoutes(), '/api/v1/scripts/list': { success: true, scripts: [] } })
    render(<ScriptsView gatewayUrl={GATEWAY_URL} showToast={() => {}} />)

    expect(await screen.findByText('No scripts saved yet.')).toBeInTheDocument()
  })

  it('shows the load error when the list endpoint answers 500', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    mockGateway({
      ...defaultRoutes(),
      '/api/v1/scripts/list': () => jsonResponse({ success: false, error: 'Script repository not initialized' }, 500),
    })
    render(<ScriptsView gatewayUrl={GATEWAY_URL} showToast={() => {}} />)

    expect(await screen.findByText('Failed to load scripts: Error: HTTP 500')).toBeInTheDocument()
  })

  it('New Script saves an empty script with the prompted name', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue('  PumpReport  ')
    const fetchMock = mockGateway({
      ...defaultRoutes(),
      '/api/v1/scripts/save': { success: true, message: 'Script saved' },
    })
    const showToast = vi.fn()
    render(<ScriptsView gatewayUrl={GATEWAY_URL} showToast={showToast} />)
    await screen.findByText('TankLevels')

    fireEvent.click(screen.getByTitle('New Script'))

    await waitFor(() => expect(showToast).toHaveBeenCalledWith('Script created successfully', 'success'))
    const [call] = callsTo(fetchMock, '/api/v1/scripts/save')
    expect(call[1]?.method).toBe('POST')
    expect(JSON.parse(String(call[1]?.body))).toEqual({ name: 'PumpReport', code: '', description: '' })
  })

  it('Delete uses POST, the only method the gateway registers for /scripts/delete', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const fetchMock = mockGateway({
      ...defaultRoutes(),
      '/api/v1/scripts/delete/TankLevels': { success: true, message: 'Script deleted successfully' },
    })
    const showToast = vi.fn()
    render(<ScriptsView gatewayUrl={GATEWAY_URL} showToast={showToast} />)
    await screen.findByText('TankLevels')

    fireEvent.click(screen.getByLabelText('Delete TankLevels'))

    await waitFor(() => expect(showToast).toHaveBeenCalledWith('Script deleted successfully', 'success'))
    const [call] = callsTo(fetchMock, '/api/v1/scripts/delete/TankLevels')
    expect(call[1]?.method).toBe('POST')
  })

  it('New Folder rejects a folder name that already exists', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue('Reports')
    mockGateway(defaultRoutes())
    const showToast = vi.fn()
    render(<ScriptsView gatewayUrl={GATEWAY_URL} showToast={showToast} />)
    await screen.findByText('TankLevels')

    fireEvent.click(screen.getByTitle('New Folder'))
    expect(showToast).toHaveBeenCalledWith('Folder "Reports" already exists.', 'error')
  })
})
