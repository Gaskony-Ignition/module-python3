import { describe, it, expect, vi, afterEach, beforeAll } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import IDEView from './IDEView'
import { mockGateway, defaultRoutes, callsTo, GATEWAY_PREFIX } from '../test/gatewayMock'

const GATEWAY_URL = window.location.origin + GATEWAY_PREFIX

// jsdom has no layout engine; CodeMirror measures text ranges after each update.
beforeAll(() => {
  const emptyRects = () => Object.assign([], { item: () => null }) as unknown as DOMRectList
  Range.prototype.getClientRects = emptyRects
  Range.prototype.getBoundingClientRect = () => new DOMRect(0, 0, 0, 0)
})

describe('IDEView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('runs the editor contents via POST /exec and shows the result', async () => {
    const fetchMock = mockGateway({
      ...defaultRoutes(),
      '/api/v1/exec': { success: true, result: 'hello from python' },
    })
    render(<IDEView gatewayUrl={GATEWAY_URL} />)
    expect(screen.getByText('Run a script to see output here. (Ctrl+Enter)')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Run script' }))

    expect(await screen.findByText('hello from python')).toBeInTheDocument()
    const [call] = callsTo(fetchMock, '/api/v1/exec')
    expect(call[1]?.method).toBe('POST')
    expect(JSON.parse(String(call[1]?.body))).toMatchObject({
      code: '# Create or load a script to get started\n',
      variables: {},
    })
  })

  it('lists saved scripts in the Load Script menu', async () => {
    const fetchMock = mockGateway(defaultRoutes())
    render(<IDEView gatewayUrl={GATEWAY_URL} />)

    fireEvent.click(screen.getByRole('button', { name: 'Load Script' }))
    await waitFor(() => expect(callsTo(fetchMock, '/api/v1/scripts/list')).toHaveLength(1))
    expect(await screen.findByText('TankLevels')).toBeInTheDocument()
    expect(screen.getByText('Housekeeping')).toBeInTheDocument()
  })
})
