import { useState, useEffect, useRef, useCallback } from 'react'
import { RefreshCw, Loader, ArrowDownToLine, Pause, Play, ScrollText } from 'lucide-react'
import PageHeader from './PageHeader'
import './LogsView.css'

const AUTO_REFRESH_MS = 10_000
const DEFAULT_FILTER = 'Python3'

interface LogEntry {
  id: number
  timestamp: string
  level: string
  message: string
}

interface LogsResponse {
  success: boolean
  entries: LogEntry[]
  count: number
  total: number
  hasMore: boolean
  warning?: string
}

interface Props {
  gatewayUrl: string
}

function LogsView({ gatewayUrl }: Props) {
  const [entries, setEntries] = useState<LogEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [level, setLevel] = useState('ALL')
  const [filter, setFilter] = useState(DEFAULT_FILTER)
  const [autoScroll, setAutoScroll] = useState(true)
  const [paused, setPaused] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [total, setTotal] = useState(0)
  const [refreshing, setRefreshing] = useState(false)

  const bodyRef = useRef<HTMLDivElement>(null)
  const timerRef = useRef<number | null>(null)
  const filterDebounceRef = useRef<number | null>(null)
  const [debouncedFilter, setDebouncedFilter] = useState(DEFAULT_FILTER)

  // Debounce the text filter
  useEffect(() => {
    if (filterDebounceRef.current) clearTimeout(filterDebounceRef.current)
    filterDebounceRef.current = window.setTimeout(() => setDebouncedFilter(filter), 400)
    return () => { if (filterDebounceRef.current) clearTimeout(filterDebounceRef.current) }
  }, [filter])

  const fetchLogs = useCallback(async (append = false, afterOffset = 0) => {
    try {
      const params = new URLSearchParams({ lines: '100' })
      if (level !== 'ALL') params.set('level', level)
      if (debouncedFilter) params.set('filter', debouncedFilter)
      if (afterOffset > 0) params.set('after', String(afterOffset))

      const res = await fetch(`${gatewayUrl}/api/v1/logs?${params}`, {
        signal: AbortSignal.timeout(10000),
        credentials: 'same-origin',
      })
      if (!res.ok) return

      const raw = await res.json()
      const data: LogsResponse = raw.data || raw

      if (data.success !== false) {
        if (append) {
          setEntries(prev => [...prev, ...data.entries])
        } else {
          setEntries(data.entries)
        }
        setHasMore(data.hasMore || false)
        setTotal(data.total || 0)
      }
    } catch {
      // Silently fail on fetch errors
    }
  }, [gatewayUrl, level, debouncedFilter])

  // Initial fetch and auto-refresh (respects paused state)
  useEffect(() => {
    let mounted = true

    const run = async () => {
      setLoading(true)
      await fetchLogs()
      if (mounted) setLoading(false)
    }

    run()

    if (!paused) {
      timerRef.current = window.setInterval(() => {
        if (mounted) fetchLogs()
      }, AUTO_REFRESH_MS)
    }

    return () => {
      mounted = false
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [fetchLogs, paused])

  // Auto-scroll to bottom when new entries arrive
  useEffect(() => {
    if (autoScroll && bodyRef.current) {
      bodyRef.current.scrollTop = 0 // Newest entries are at top
    }
  }, [entries, autoScroll])

  const handleRefresh = async () => {
    setRefreshing(true)
    await fetchLogs()
    setRefreshing(false)
  }

  const handleLoadMore = async () => {
    // Use the last entry's ID for cursor-based pagination
    const lastEntry = entries[entries.length - 1]
    const afterId = lastEntry ? lastEntry.id : 0
    await fetchLogs(true, afterId)
  }

  return (
    <div className="logs-view">
      {/* Header */}
      <PageHeader icon={ScrollText} title="Logs" subtitle="Real-time module log viewer">
        <select className="logs-level-select" value={level} onChange={e => setLevel(e.target.value)}>
          <option value="ALL">All Levels</option>
          <option value="DEBUG">DEBUG+</option>
          <option value="INFO">INFO+</option>
          <option value="WARN">WARN+</option>
          <option value="ERROR">ERROR only</option>
        </select>
        <input className="logs-filter-input" type="text" placeholder="Filter logs..." value={filter} onChange={e => setFilter(e.target.value)} spellCheck={false} />
        <button className={`logs-pause-btn ${paused ? 'logs-pause-btn--paused' : ''}`} onClick={() => setPaused(v => !v)} title={paused ? 'Resume live logs' : 'Pause live logs'}>
          {paused ? <Play size={12} /> : <Pause size={12} />}
          {paused ? 'Resume' : 'Pause'}
        </button>
        <button className={`logs-autoscroll-btn ${autoScroll ? 'logs-autoscroll-btn--active' : ''}`} onClick={() => setAutoScroll(v => !v)} title={autoScroll ? 'Auto-scroll enabled' : 'Auto-scroll disabled'}>
          <ArrowDownToLine size={12} />
        </button>
        <button className="logs-refresh-btn" onClick={handleRefresh} disabled={refreshing || loading}>
          {refreshing || loading ? <Loader size={12} className="spin-sm" /> : <RefreshCw size={12} />}
          Refresh
        </button>
      </PageHeader>

      {/* Log entries */}
      <div className="logs-body" ref={bodyRef}>
        {loading && entries.length === 0 ? (
          <div className="logs-empty">
            <Loader size={16} className="spin-sm" /> Loading logs...
          </div>
        ) : entries.length === 0 ? (
          <div className="logs-empty">No log entries found</div>
        ) : (
          <>
            {entries.map((entry, i) => (
              <div key={`${entry.id}-${i}`} className="logs-entry">
                <span className="logs-entry__timestamp">{entry.timestamp}</span>
                <span className={`logs-entry__level logs-entry__level--${entry.level}`}>
                  {entry.level}
                </span>
                <span className="logs-entry__message">{entry.message}</span>
              </div>
            ))}
            {hasMore && (
              <div className="logs-load-more">
                <button className="logs-load-more-btn" onClick={handleLoadMore}>
                  Load more...
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {/* Status bar */}
      <div className="logs-status">
        <span>{entries.length} of {total} entries</span>
        <span>{paused ? 'Paused' : `Auto-refresh: ${AUTO_REFRESH_MS / 1000}s`}</span>
      </div>
    </div>
  )
}

export default LogsView
