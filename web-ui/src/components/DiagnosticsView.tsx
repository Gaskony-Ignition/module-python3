import { useState, useEffect, useRef, useCallback } from 'react'
import { RefreshCw, ChevronDown, ChevronRight, Loader, Activity, Pause, Play, ArrowDownToLine } from 'lucide-react'
import { apiPost } from '../utils/api'
import PageHeader from './PageHeader'
import PoolStatsPanel from './PoolStatsPanel'
import MetricsPanel from './MetricsPanel'
import './DiagnosticsView.css'

const AUTO_REFRESH_MS = 10_000
const LOG_POLL_MS = 10_000

type LevelFilter = 'ALL' | 'ERROR' | 'WARN' | 'INFO' | 'DEBUG'
const LEVEL_FILTERS: LevelFilter[] = ['ALL', 'ERROR', 'WARN', 'INFO', 'DEBUG']

interface LogEntry {
  id: number
  timestamp: string
  level: string
  message: string
}

interface Props {
  gatewayUrl: string
}

function DiagnosticsView({ gatewayUrl }: Props) {
  const [health, setHealth] = useState<Record<string, unknown> | null>(null)
  const [poolStats, setPoolStats] = useState<Record<string, unknown> | null>(null)
  const [diagnostics, setDiagnostics] = useState<Record<string, unknown> | null>(null)
  const [metrics, setMetrics] = useState<Record<string, unknown> | null>(null)
  const [loading, setLoading] = useState(true)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [secondsAgo, setSecondsAgo] = useState(0)
  const [rawExpanded, setRawExpanded] = useState(false)
  const [refreshing, setRefreshing] = useState(false)

  // Logs state
  const [logEntries, setLogEntries] = useState<LogEntry[]>([])
  const [logLevel, setLogLevel] = useState<LevelFilter>('ALL')
  const [logPaused, setLogPaused] = useState(false)
  const [logAutoScroll, setLogAutoScroll] = useState(true)
  const logBodyRef = useRef<HTMLDivElement>(null)
  const logTimerRef = useRef<number | null>(null)

  const timerRef = useRef<number | null>(null)
  const clockRef = useRef<number | null>(null)

  const fetchAll = useCallback(async () => {
    const endpoints = [
      `${gatewayUrl}/api/v1/health`,
      `${gatewayUrl}/api/v1/pool-stats`,
      `${gatewayUrl}/api/v1/diagnostics`,
      `${gatewayUrl}/api/v1/metrics`,
    ]

    const results = await Promise.allSettled(
      endpoints.map((url) =>
        fetch(url, { credentials: 'same-origin' })
          .then((r) => (r.ok ? r.json() : null))
          .then((raw) => (raw ? raw.data || raw : null))
          .catch(() => null)
      )
    )

    const [h, ps, diag, m] = results.map((r) =>
      r.status === 'fulfilled' ? r.value : null
    )

    setHealth(h as Record<string, unknown> | null)
    setPoolStats(ps as Record<string, unknown> | null)
    setDiagnostics(diag as Record<string, unknown> | null)
    setMetrics(m as Record<string, unknown> | null)
    setLastUpdated(new Date())
    setSecondsAgo(0)
  }, [gatewayUrl])

  // Auto-refresh loop
  useEffect(() => {
    let mounted = true

    const run = async () => {
      setLoading(true)
      await fetchAll()
      if (mounted) setLoading(false)
    }

    run()

    timerRef.current = window.setInterval(async () => {
      if (mounted) await fetchAll()
    }, AUTO_REFRESH_MS)

    return () => {
      mounted = false
      if (timerRef.current) window.clearInterval(timerRef.current)
      if (clockRef.current) window.clearInterval(clockRef.current)
    }
  }, [fetchAll])

  // "Last updated X seconds ago" ticker
  useEffect(() => {
    if (clockRef.current) window.clearInterval(clockRef.current)
    clockRef.current = window.setInterval(() => {
      setSecondsAgo((s) => s + 1)
    }, 1000)
    return () => {
      if (clockRef.current) window.clearInterval(clockRef.current)
    }
  }, [lastUpdated])

  const handleRefresh = async () => {
    setRefreshing(true)
    await fetchAll()
    setRefreshing(false)
  }

  const handleResizePool = async (newSize: number) => {
    const result = await apiPost<{ success: boolean; error?: string }>('/api/v1/pool-size', { size: newSize })
    if (result && !result.success) {
      throw new Error(result.error || 'Pool resize failed')
    }
    await fetchAll()
  }

  // --- Log fetching ---
  const fetchLogs = useCallback(async () => {
    try {
      const params = new URLSearchParams({ lines: '100', filter: 'Python3' })
      if (logLevel !== 'ALL') params.set('level', logLevel)

      const res = await fetch(`${gatewayUrl}/api/v1/logs?${params}`, {
        signal: AbortSignal.timeout(10000),
        credentials: 'same-origin',
      })
      if (!res.ok) return

      const raw = await res.json()
      const data = raw.data || raw
      if (data.success !== false && data.entries) {
        setLogEntries(data.entries)
      }
    } catch {
      // Silently fail
    }
  }, [gatewayUrl, logLevel])

  // Log polling
  useEffect(() => {
    let mounted = true
    fetchLogs()

    if (!logPaused) {
      logTimerRef.current = window.setInterval(() => {
        if (mounted) fetchLogs()
      }, LOG_POLL_MS)
    }

    return () => {
      mounted = false
      if (logTimerRef.current) clearInterval(logTimerRef.current)
    }
  }, [fetchLogs, logPaused])

  // Auto-scroll logs
  useEffect(() => {
    if (logAutoScroll && logBodyRef.current) {
      logBodyRef.current.scrollTop = logBodyRef.current.scrollHeight
    }
  }, [logEntries, logAutoScroll])

  // --- Health status ---
  const overallStatus: string =
    (health?.status as string) ||
    (health?.overall as string) ||
    (health?.healthy ? 'HEALTHY' : health?.healthy === false ? 'DOWN' : 'UNKNOWN')
  const statusNorm = overallStatus.toUpperCase()

  // --- Pool stats normalization ---
  const normalizedPool = poolStats
    ? {
        poolSize: (poolStats.poolSize as number) ?? 0,
        activeExecutors: (poolStats.activeExecutors as number) ?? (poolStats.inUse as number) ?? 0,
        availableExecutors:
          (poolStats.availableExecutors as number) ?? (poolStats.available as number) ?? 0,
        borrowedCount: (poolStats.borrowedCount as number) ?? (poolStats.totalBorrowed as number) ?? 0,
        healthCheckStatus:
          (poolStats.healthCheckStatus as string) ??
          (poolStats.status as string) ??
          'Unknown',
      }
    : null

  // --- Metrics normalization ---
  const normalizedMetrics = metrics
    ? {
        totalExecutions: (metrics.totalExecutions as number) ?? 0,
        successfulExecutions: (metrics.successfulExecutions as number) ?? 0,
        failedExecutions: (metrics.failedExecutions as number) ?? 0,
        averageLatencyMs:
          (metrics.averageLatencyMs as number) ?? (metrics.avgLatencyMs as number) ?? 0,
        maxLatencyMs: (metrics.maxLatencyMs as number) ?? 0,
        errorRate: (metrics.errorRate as number) ?? 0,
      }
    : null

  return (
    <div className="diagnostics-view">
      {/* Header */}
      <PageHeader icon={Activity} title="Diagnostics" subtitle={lastUpdated ? `Last updated: ${secondsAgo}s ago` : 'Loading...'}>
        <button className={`diag-refresh-btn ${refreshing ? 'spinning' : ''}`} onClick={handleRefresh} disabled={refreshing || loading} title="Refresh diagnostics">
          {loading || refreshing ? <Loader size={13} className="diag-refresh-btn__spinner" /> : <RefreshCw size={13} />}
          Refresh
        </button>
      </PageHeader>

      {/* Scrollable body */}
      <div className="diag-body">
        {/* 1. Pool Stats */}
        <PoolStatsPanel stats={normalizedPool} onResizePool={handleResizePool} />

        {/* 3. Execution Metrics */}
        <MetricsPanel metrics={normalizedMetrics} />

        {/* 4. Raw Diagnostics (collapsible) */}
        <div className="diag-panel">
          <button
            className="diag-raw-toggle"
            onClick={() => setRawExpanded((v) => !v)}
          >
            {rawExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            Raw Diagnostics
          </button>
          {rawExpanded && (
            <pre className="diag-raw-json">
              {diagnostics
                ? JSON.stringify(diagnostics, null, 2)
                : '(no diagnostics data)'}
            </pre>
          )}
        </div>

        {/* 5. Module Logs */}
        <div className="diag-panel diag-logs-panel">
          <div className="diag-logs-header">
            <span className="diag-logs-header__title">Module Logs</span>
            <div className="diag-logs-controls">
              <div className="diag-logs-filters">
                {LEVEL_FILTERS.map((lv) => (
                  <button
                    key={lv}
                    className={`diag-logs-pill ${logLevel === lv ? 'diag-logs-pill--active' : ''}`}
                    onClick={() => setLogLevel(lv)}
                  >
                    {lv}
                  </button>
                ))}
              </div>
              <button
                className={`diag-logs-icon-btn ${logPaused ? 'diag-logs-icon-btn--warning' : ''}`}
                onClick={() => setLogPaused((v) => !v)}
                title={logPaused ? 'Resume live logs' : 'Pause live logs'}
              >
                {logPaused ? <Play size={12} /> : <Pause size={12} />}
              </button>
              <button
                className={`diag-logs-icon-btn ${logAutoScroll ? 'diag-logs-icon-btn--active' : ''}`}
                onClick={() => setLogAutoScroll((v) => !v)}
                title={logAutoScroll ? 'Auto-scroll enabled' : 'Auto-scroll disabled'}
              >
                <ArrowDownToLine size={12} />
              </button>
            </div>
          </div>
          <div className="diag-logs-body" ref={logBodyRef}>
            {logEntries.length === 0 ? (
              <div className="diag-logs-empty">No log entries found</div>
            ) : (
              logEntries.map((entry, i) => (
                <div key={`${entry.id}-${i}`} className="diag-logs-entry">
                  <span className="diag-logs-entry__ts">{entry.timestamp}</span>
                  <span className={`diag-logs-entry__level diag-logs-entry__level--${entry.level}`}>
                    {entry.level}
                  </span>
                  <span className="diag-logs-entry__msg">{entry.message}</span>
                </div>
              ))
            )}
          </div>
          <div className="diag-logs-status">
            <span>{logEntries.length} entries (module only)</span>
            <span>{logPaused ? 'Paused' : `Auto-refresh: ${LOG_POLL_MS / 1000}s`}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default DiagnosticsView
