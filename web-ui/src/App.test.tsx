import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import App from './App'
import { mockGateway, defaultRoutes, jsonResponse } from './test/gatewayMock'

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('renders the dashboard with live gateway status on load', async () => {
    mockGateway(defaultRoutes())
    render(<App />)

    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(await screen.findByText('Healthy')).toBeInTheDocument()
    expect(screen.getByText('3.11')).toBeInTheDocument()
    expect(screen.getByText('3.12')).toBeInTheDocument()
    // Sidebar shows the module version reported by /version, not its fallback.
    expect(await screen.findByText('v9.8.7')).toBeInTheDocument()
  })

  it('shows the login prompt when the gateway rejects the health check', async () => {
    mockGateway({ ...defaultRoutes(), '/api/v1/health': () => jsonResponse({ error: 'Unauthorized' }, 401) })
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Authentication Required' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Log In to Gateway' })).toHaveAttribute('href', '/web/login')
  })

  it.each([
    ['Scripts', 'Scripts', 'TankLevels'],
    ['Python Versions', 'Python Versions', 'Python 3.13'],
    ['Packages', 'Packages', 'numpy'],
    ['Diagnostics', 'Diagnostics', 'Python3ProcessPool started with 3 executors'],
  ])('navigates to %s from the sidebar', async (navLabel, heading, content) => {
    mockGateway(defaultRoutes())
    render(<App />)

    const nav = screen.getByRole('navigation')
    fireEvent.click(within(nav).getByRole('button', { name: navLabel }))

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
    expect(await screen.findByText(content)).toBeInTheDocument()
    expect(within(nav).getByRole('button', { name: navLabel })).toHaveAttribute('aria-current', 'page')
  })

  it('navigates to the IDE from the dashboard quick action', async () => {
    mockGateway(defaultRoutes())
    render(<App />)

    fireEvent.click(screen.getByRole('button', { name: /New Script/ }))
    expect(await screen.findByRole('button', { name: 'Run script' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Load Script' })).toBeInTheDocument()
  })
})
