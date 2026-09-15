import { useState, useRef, useEffect } from 'react'
import { Loader } from 'lucide-react'

interface PoolStats {
  poolSize: number
  activeExecutors: number
  availableExecutors: number
  borrowedCount: number
  healthCheckStatus: string
}

interface PoolStatsPanelProps {
  stats: PoolStats | null
  onResizePool?: (newSize: number) => Promise<void>
}

function PoolStatsPanel({ stats, onResizePool }: PoolStatsPanelProps) {
  const [editing, setEditing] = useState(false)
  const [editValue, setEditValue] = useState('')
  const [resizing, setResizing] = useState(false)
  const [resizeError, setResizeError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editing && inputRef.current) {
      inputRef.current.focus()
      inputRef.current.select()
    }
  }, [editing])

  if (!stats) {
    return (
      <div className="diag-panel">
        <h3 className="diag-panel__title">Process Pool</h3>
        <p className="diag-panel__empty">No pool data available.</p>
      </div>
    )
  }

  const { poolSize, activeExecutors, availableExecutors, borrowedCount, healthCheckStatus } = stats

  const activePct = poolSize > 0 ? Math.round((activeExecutors / poolSize) * 100) : 0
  const availPct = poolSize > 0 ? Math.round((availableExecutors / poolSize) * 100) : 0

  // Bar fill colour: green if low utilisation, yellow if > 60%, red if > 85%
  const barClass =
    activePct >= 85
      ? 'pool-util-fill--danger'
      : activePct >= 60
      ? 'pool-util-fill--warn'
      : 'pool-util-fill--ok'

  const isHealthy =
    healthCheckStatus?.toLowerCase() === 'healthy' ||
    healthCheckStatus?.toLowerCase() === 'ok' ||
    healthCheckStatus?.toLowerCase() === 'running'

  const handleStartEdit = () => {
    if (!onResizePool) return
    setEditValue(String(poolSize))
    setResizeError(null)
    setEditing(true)
  }

  const handleCommitResize = async () => {
    const newSize = parseInt(editValue, 10)
    if (isNaN(newSize) || newSize < 1 || newSize > 20) {
      setResizeError('Must be 1–20')
      return
    }
    if (newSize === poolSize) {
      setEditing(false)
      return
    }
    setResizing(true)
    setResizeError(null)
    try {
      await onResizePool!(newSize)
      setEditing(false)
    } catch (err) {
      setResizeError(err instanceof Error ? err.message : 'Resize failed')
    } finally {
      setResizing(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleCommitResize()
    if (e.key === 'Escape') setEditing(false)
  }

  return (
    <div className="diag-panel">
      <h3 className="diag-panel__title">Process Pool</h3>

      {/* Utilisation bar */}
      <div className="pool-util">
        <div className="pool-util__labels">
          <span className="pool-util__label">Utilisation</span>
          <span className="pool-util__pct">{activePct}%</span>
        </div>
        <div className="pool-util__bar">
          <div
            className={`pool-util__fill pool-util__fill--active ${barClass}`}
            style={{ width: `${activePct}%` }}
          />
          <div
            className="pool-util__fill pool-util__fill--avail"
            style={{ width: `${availPct}%`, marginLeft: `${activePct}%` }}
          />
        </div>
        <div className="pool-util__legend">
          <span className="pool-legend pool-legend--active">Active</span>
          <span className="pool-legend pool-legend--avail">Available</span>
        </div>
      </div>

      {/* Stats rows */}
      <div className="diag-stats-grid">
        <div className="diag-stat">
          <span className="diag-stat__label">Pool Size</span>
          {editing ? (
            <span className="diag-stat__value pool-size-edit">
              <input
                ref={inputRef}
                type="number"
                min={1}
                max={20}
                value={editValue}
                onChange={e => setEditValue(e.target.value)}
                onBlur={handleCommitResize}
                onKeyDown={handleKeyDown}
                disabled={resizing}
                className="pool-size-input"
              />
              {resizing && <Loader size={12} className="spin-sm" />}
            </span>
          ) : (
            <span
              className={`diag-stat__value ${onResizePool ? 'pool-size-clickable' : ''}`}
              onClick={handleStartEdit}
              title={onResizePool ? 'Click to change pool size (1–20)' : undefined}
            >
              {poolSize}
            </span>
          )}
          {resizeError && <span className="pool-size-error">{resizeError}</span>}
        </div>
        <div className="diag-stat">
          <span className="diag-stat__label">Active</span>
          <span className="diag-stat__value">{activeExecutors}</span>
        </div>
        <div className="diag-stat">
          <span className="diag-stat__label">Available</span>
          <span className="diag-stat__value">{availableExecutors}</span>
        </div>
        <div className="diag-stat">
          <span className="diag-stat__label">Total Borrowed</span>
          <span className="diag-stat__value">{borrowedCount}</span>
        </div>
      </div>

      {/* Health check status */}
      <div className="pool-health-row">
        <span
          className={`pool-health-dot ${isHealthy ? 'pool-health-dot--healthy' : 'pool-health-dot--down'}`}
        />
        <span className="pool-health-label">
          Health check: <strong>{healthCheckStatus || 'Unknown'}</strong>
        </span>
      </div>
    </div>
  )
}

export default PoolStatsPanel
