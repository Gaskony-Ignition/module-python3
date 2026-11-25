/**
 * Test helpers: a routed `fetch` stand-in for the Python 3 Integration gateway.
 *
 * Fixture payloads mirror what the gateway route handlers actually return
 * (MonitoringHandlers, ExecutionHandlers, ScriptAndPackageHandlers,
 * Python3LogsHandler, Python3MetricsCollector) so the UI is exercised
 * against real response shapes rather than invented ones.
 */
import { vi } from 'vitest'

export const GATEWAY_PREFIX = '/data/python3integration'

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function textResponse(body: string, status: number, contentType = 'text/plain'): Response {
  return new Response(body, { status, headers: { 'Content-Type': contentType } })
}

type RouteValue = unknown | ((url: URL, init?: RequestInit) => Response | Promise<Response>)

/**
 * Installs a mocked global fetch. Keys are gateway paths without the module
 * prefix (e.g. `/api/v1/health`); the longest matching prefix wins.
 * Unmatched paths answer 404 so a missing mock is obvious rather than silent.
 */
export function mockGateway(routes: Record<string, RouteValue>) {
  const keys = Object.keys(routes).sort((a, b) => b.length - a.length)
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = new URL(String(input), window.location.origin)
    const path = url.pathname.startsWith(GATEWAY_PREFIX)
      ? url.pathname.slice(GATEWAY_PREFIX.length)
      : url.pathname
    const key = keys.find(k => path === k || path.startsWith(k))
    if (key === undefined) {
      return jsonResponse({ success: false, error: `Not mocked: ${path}` }, 404)
    }
    const value = routes[key]
    if (typeof value === 'function') {
      return (value as (u: URL, i?: RequestInit) => Response | Promise<Response>)(url, init)
    }
    return jsonResponse(value)
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

/** Finds the recorded fetch calls whose URL path ends with `pathSuffix`. */
export function callsTo(fn: ReturnType<typeof mockGateway>, pathSuffix: string) {
  return fn.mock.calls.filter(([input]) =>
    new URL(String(input), window.location.origin).pathname.endsWith(pathSuffix)
  )
}

export function headersOf(init: RequestInit | undefined): Record<string, string> {
  return (init?.headers ?? {}) as Record<string, string>
}

// ---- Fixtures (shapes from the gateway Java handlers) ----

/** ExecutionHandlers.handleCreateSession */
export const sessionPayload = {
  success: true,
  token: 'csrf-token-123',
  api_token: 'api-token-456',
  expires_in: 28800,
}

/** MonitoringHandlers.handleHealthCheck */
export const healthPayload = {
  healthy: true,
  available: true,
  status: 'HEALTHY',
  timestamp: 1764028800000,
}

/** MonitoringHandlers.handleGetPoolStats (Python3ScriptModule.getPoolStats + healthCheckStatus) */
export const poolStatsPayload = {
  poolSize: 3,
  available: 2,
  inUse: 1,
  healthy: true,
  healthCheckStatus: 'Healthy',
}

/** MonitoringHandlers.handleGetVersions (PoolManager mode) */
export const versionsPayload = {
  versions: ['3.11', '3.12'],
  default: '3.11',
  count: 2,
  details: {
    '3.11': { path: '/usr/bin/python3.11', isDefault: true, poolSize: 3, available: 2, healthy: true },
    '3.12': { path: '/usr/bin/python3.12', isDefault: false, poolSize: 3, available: 3, healthy: true },
  },
  success: true,
  timestamp: 1764028800000,
}

/** MonitoringHandlers.handleGetVersion */
export const versionPayload = {
  version: '3.11.9',
  available: true,
  pythonVersion: '3.11.9',
  moduleVersion: '9.8.7',
}

/** MonitoringHandlers.handleGetMetrics (Python3MetricsCollector.getMetrics) */
export const metricsPayload = {
  total_executions: 42,
  successful_executions: 40,
  failed_executions: 2,
  success_rate: 95.2,
  total_execution_time_ms: 840,
  average_execution_time_ms: 20,
  min_execution_time_ms: 3,
  max_execution_time_ms: 110,
  pool_size: 3,
  active_executions: 1,
  pool_utilization: 33.3,
  pool_wait_count: 0,
  average_pool_wait_ms: 0,
  error_counts: {},
  uptime_ms: 3600000,
  uptime_hours: 1.0,
}

/** MonitoringHandlers.handleDiagnostics */
export const diagnosticsPayload = {
  available: true,
  poolStats: { poolSize: 3, available: 2, inUse: 1, healthy: true },
  versionInfo: { version: '3.11.9', available: true },
  distributionInfo: { os: 'linux' },
  timestamp: 1764028800000,
}

/** MonitoringHandlers.handleGetDistributions */
export const distributionsPayload = {
  distributions: [
    {
      version: '3.11',
      fullVersion: '3.11.9',
      installed: true,
      available: true,
      pythonPath: '/opt/ignition/data/python3-integration/python/3.11/bin/python3',
      installSizeBytes: 104857600,
      installSizeMB: 100,
      poolActive: true,
    },
    {
      version: '3.13',
      fullVersion: '3.13.1',
      installed: false,
      available: true,
      poolActive: false,
    },
  ],
  os: 'linux',
  installedCount: 1,
  success: true,
  timestamp: 1764028800000,
}

/** ScriptAndPackageHandlers.handleListScripts */
export const scriptsListPayload = {
  success: true,
  scripts: [
    {
      id: 'a1',
      name: 'TankLevels',
      description: 'Reads tank levels',
      author: 'admin',
      createdDate: '2026-11-01T09:00:00Z',
      lastModified: '2026-11-20T10:30:00Z',
      folderPath: 'Reports',
      version: 1,
    },
    {
      id: 'b2',
      name: 'Housekeeping',
      description: 'Nightly clean-up',
      author: 'admin',
      createdDate: '2026-11-02T09:00:00Z',
      lastModified: '2026-11-21T10:30:00Z',
      folderPath: '',
      version: 3,
    },
  ],
}

/** ScriptAndPackageHandlers.handleGetPackageCatalog (object map keyed by package name) */
export const catalogPayload = {
  packages: {
    numpy: {
      version: '2.1.3',
      description: 'Fundamental package for array computing',
      sizeMb: 18.5,
      importName: 'numpy',
      installed: true,
      wheels: ['numpy-2.1.3-cp311-cp311-manylinux_2_17_x86_64.whl'],
      pipPackages: ['numpy'],
      requiredFor: ['pandas'],
    },
    pandas: {
      version: '2.2.3',
      description: 'Powerful data structures for data analysis',
      sizeMb: 42.0,
      importName: 'pandas',
      installed: false,
      wheels: [],
      pipPackages: ['pandas'],
      requiredFor: [],
    },
  },
  count: 2,
}

/** Python3LogsHandler.readLogs */
export const logsPayload = {
  success: true,
  entries: [
    { id: 101, timestamp: '2026-11-25 08:00:01', level: 'INFO', message: 'Python3ProcessPool started with 3 executors' },
    { id: 102, timestamp: '2026-11-25 08:00:05', level: 'WARN', message: 'Executor 2 slow to respond' },
  ],
  count: 2,
  total: 2,
  hasMore: false,
}

/** The standard set of read-only routes the app hits on load. */
export function defaultRoutes(): Record<string, RouteValue> {
  return {
    '/auth/session': sessionPayload,
    '/api/v1/health': healthPayload,
    '/api/v1/pool-stats': poolStatsPayload,
    '/api/v1/versions': versionsPayload,
    '/api/v1/version': versionPayload,
    '/api/v1/metrics': metricsPayload,
    '/api/v1/diagnostics': diagnosticsPayload,
    '/api/v1/distributions': distributionsPayload,
    '/api/v1/scripts/list': scriptsListPayload,
    '/api/v1/packages/catalog': catalogPayload,
    '/api/v1/logs': logsPayload,
  }
}
