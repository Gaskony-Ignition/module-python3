import { useState, useEffect, useCallback, useRef } from 'react'
import {
  Activity, Layers, Code2, Terminal, Package, ExternalLink,
  RefreshCw, AlertCircle, Zap, BarChart2, LayoutDashboard
} from 'lucide-react'
import PageHeader from './PageHeader'
import StatCard from './StatCard'
import './DashboardView.css'

interface Props {
  gatewayUrl: string
  onNavigate: (view: string) => void
}

interface HealthData {
  status?: string
  healthy?: boolean
  pythonVersion?: string
  poolSize?: number
  message?: string
}

interface PoolStats {
  activeExecutors?: number
  availableExecutors?: number
  poolSize?: number
  borrowed?: number
  available?: number
  inUse?: number
}

interface VersionEntry {
  version?: string
  path?: string
  active?: boolean
  default?: boolean
}

interface VersionsData {
  versions?: VersionEntry[]
  installedVersions?: string[]
  count?: number
}

interface MetricsData {
  totalExecutions?: number
  successfulExecutions?: number
  failedExecutions?: number
  averageLatencyMs?: number
  avgLatency?: number
  errorRate?: number
  errorsPercentage?: number
}

type LoadState = 'loading' | 'loaded' | 'error'

const REFRESH_INTERVAL_MS = 30_000

function DashboardView({ gatewayUrl, onNavigate }: Props) {
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [health, setHealth] = useState<HealthData | null>(null)
  const [poolStats, setPoolStats] = useState<PoolStats | null>(null)
  const [versions, setVersions] = useState<VersionsData | null>(null)
  const [metrics, setMetrics] = useState<MetricsData | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [lastRefreshed, setLastRefreshed] = useState<Date | null>(null)
  const timerRef = useRef<number | null>(null)

  const fetchAll = useCallback(async (isManual = false) => {
    if (isManual) setRefreshing(true)

    const results = await Promise.allSettled([
      fetch(`${gatewayUrl}/api/v1/health`, { signal: AbortSignal.timeout(8000) }).then(r => r.json()),
      fetch(`${gatewayUrl}/api/v1/pool-stats`, { signal: AbortSignal.timeout(8000) }).then(r => r.json()),
      fetch(`${gatewayUrl}/api/v1/versions`, { signal: AbortSignal.timeout(8000) }).then(r => r.json()),
      fetch(`${gatewayUrl}/api/v1/metrics`, { signal: AbortSignal.timeout(8000) }).then(r => r.json()),
    ])

    const unwrap = (r: PromiseSettledResult<unknown>): unknown =>
      r.status === 'fulfilled' ? ((r.value as Record<string, unknown>).data ?? r.value) : null

    const [healthRaw, poolRaw, versionsRaw, metricsRaw] = results.map(unwrap)

    if (healthRaw) setHealth(healthRaw as HealthData)
    if (poolRaw) setPoolStats(poolRaw as PoolStats)
    if (versionsRaw) setVersions(versionsRaw as VersionsData)
    if (metricsRaw) setMetrics(metricsRaw as MetricsData)

    setLoadState('loaded')
    setLastRefreshed(new Date())
    if (isManual) setRefreshing(false)
  }, [gatewayUrl])

  useEffect(() => {
    fetchAll()

    const scheduleNext = () => {
      timerRef.current = window.setTimeout(() => {
        fetchAll()
        scheduleNext()
      }, REFRESH_INTERVAL_MS)
    }
    scheduleNext()

    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    }
  }, [fetchAll])

  // ---- Health card ----
  const healthStatus = (): 'healthy' | 'degraded' | 'down' => {
    if (!health) return 'down'
    const s = health.status?.toLowerCase() ?? ''
    if (s === 'healthy' || health.healthy === true) return 'healthy'
    if (s === 'degraded') return 'degraded'
    return 'down'
  }

  const healthLabel = () => {
    const s = healthStatus()
    if (s === 'healthy') return 'Healthy'
    if (s === 'degraded') return 'Degraded'
    return 'Down'
  }

  // ---- Pool card ----
  const active = poolStats?.activeExecutors ?? poolStats?.inUse ?? poolStats?.borrowed ?? 0
  const total = poolStats?.poolSize ?? 0
  const available = poolStats?.availableExecutors ?? poolStats?.available ?? (total - active)
  const barPct = total > 0 ? Math.round((active / total) * 100) : 0
  const barClass =
    barPct >= 100 ? 'pool-bar__fill--full'
    : barPct >= 70 ? 'pool-bar__fill--busy'
    : ''

  // ---- Versions card ----
  const versionList: VersionEntry[] = (() => {
    if (!versions) return []
    if (Array.isArray(versions.versions)) return versions.versions
    if (Array.isArray(versions.installedVersions)) {
      return (versions.installedVersions as string[]).map((v: string) => ({ version: v }))
    }
    return []
  })()

  // ---- Metrics card ----
  const totalExec = metrics?.totalExecutions ?? 0
  const avgLatency = metrics?.averageLatencyMs ?? metrics?.avgLatency ?? null
  const errorRate = metrics?.errorRate ?? metrics?.errorsPercentage ?? null

  const isLoading = loadState === 'loading'

  return (
    <div className="dashboard-view">
      {/* Header */}
      <PageHeader icon={LayoutDashboard} title="Dashboard" subtitle={lastRefreshed ? `Last updated ${lastRefreshed.toLocaleTimeString()}` : 'Loading gateway status...'}>
        <button className={`dashboard-refresh-btn ${refreshing ? 'spinning' : ''}`} onClick={() => fetchAll(true)} disabled={refreshing} aria-label="Refresh dashboard">
          <RefreshCw size={13} />
          {refreshing ? 'Refreshing...' : 'Refresh'}
        </button>
      </PageHeader>

      {/* Stats grid */}
      <div className="dashboard-stats-grid">
        {/* Card 1: Health */}
        <StatCard
          title="Health Status"
          icon={<Activity size={13} />}
          status={isLoading ? 'neutral' : healthStatus()}
          loading={isLoading}
        >
          <div className="health-status">
            <span className={`health-status__dot health-status__dot--${healthStatus()}`} />
            <span className={`health-status__label health-status__label--${healthStatus()}`}>
              {healthLabel()}
            </span>
          </div>
          {health?.pythonVersion && (
            <div className="health-status__detail">Python {health.pythonVersion}</div>
          )}
          {health?.message && (
            <div className="health-status__detail">{health.message}</div>
          )}
        </StatCard>

        {/* Card 2: Pool Stats */}
        <StatCard
          title="Process Pool"
          icon={<Layers size={13} />}
          loading={isLoading}
        >
          <div className="pool-stat-row">
            <span className="pool-stat-row__label">Active</span>
            <span className="pool-stat-row__value">{active}</span>
          </div>
          <div className="pool-stat-row">
            <span className="pool-stat-row__label">Available</span>
            <span className="pool-stat-row__value">{available}</span>
          </div>
          <div className="pool-stat-row">
            <span className="pool-stat-row__label">Total</span>
            <span className="pool-stat-row__value">{total}</span>
          </div>
          <div className="pool-bar" title={`${barPct}% utilization`}>
            <div
              className={`pool-bar__fill ${barClass}`}
              style={{ width: `${barPct}%` }}
            />
          </div>
        </StatCard>

        {/* Card 3: Python Versions */}
        <StatCard
          title="Python Versions"
          icon={<Code2 size={13} />}
          loading={isLoading}
        >
          <div className="versions-count">{versionList.length}</div>
          {versionList.length > 0 ? (
            <div className="versions-list">
              {versionList.map((v, i) => (
                <span
                  key={i}
                  className={`version-tag ${v.active || v.default ? 'version-tag--active' : ''}`}
                  title={v.path ?? undefined}
                >
                  {v.version ?? String(v)}
                </span>
              ))}
            </div>
          ) : (
            <div className="health-status__detail">No versions detected</div>
          )}
        </StatCard>

        {/* Card 4: Execution Metrics */}
        <StatCard
          title="Execution Metrics"
          icon={<BarChart2 size={13} />}
          loading={isLoading}
        >
          <div className="metric-row">
            <span className="metric-row__label">Total Executions</span>
            <span className="metric-row__value">{totalExec.toLocaleString()}</span>
          </div>
          <div className="metric-divider" />
          <div className="metric-row">
            <span className="metric-row__label">Avg Latency</span>
            <span className="metric-row__value">
              {avgLatency !== null ? `${Math.round(avgLatency)} ms` : '—'}
            </span>
          </div>
          <div className="metric-row">
            <span className="metric-row__label">Error Rate</span>
            <span className={`metric-row__value ${errorRate && errorRate > 10 ? 'metric-row__value--error' : ''}`}>
              {errorRate !== null ? `${Number(errorRate).toFixed(1)}%` : '—'}
            </span>
          </div>
        </StatCard>
      </div>

      {/* Quick Actions */}
      <div>
        <div className="dashboard-section-label">Quick Actions</div>
        <div className="dashboard-quick-actions">
          <button className="action-btn action-btn--primary" onClick={() => onNavigate('ide')}>
            <span className="action-btn__icon"><Zap size={14} /></span>
            New Script
          </button>
          <button className="action-btn" onClick={() => onNavigate('terminal')}>
            <span className="action-btn__icon"><Terminal size={14} /></span>
            Open Terminal
          </button>
          <button className="action-btn" onClick={() => onNavigate('packages')}>
            <span className="action-btn__icon"><Package size={14} /></span>
            Manage Packages
          </button>
          <button
            className="action-btn"
            onClick={() => window.open('/res/python3integration/standalone.html', '_blank')}
          >
            <span className="action-btn__icon"><ExternalLink size={14} /></span>
            Open Dedicated Page
          </button>
        </div>
      </div>

      {/* Error fallback */}
      {loadState === 'error' && (
        <div className="dashboard-error">
          <AlertCircle size={16} />
          Unable to load gateway data. Check your connection and try refreshing.
        </div>
      )}

      {/* Invisible element to prevent layout collapse on tiny screen */}
      <div style={{ flexShrink: 0, height: 1 }} />
    </div>
  )
}

export default DashboardView
