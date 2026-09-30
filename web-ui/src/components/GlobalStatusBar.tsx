import { useState, useCallback } from 'react'
import { Cpu, MemoryStick, Loader } from 'lucide-react'
import { checkAuthResponse } from '../utils/authCheck'
import { useVisibilityAwarePolling } from '../hooks/useVisibilityAwarePolling'
import './GlobalStatusBar.css'

interface Props {
  gatewayUrl: string
}

interface PoolStats {
  activeExecutors?: number
  availableExecutors?: number
  poolSize?: number
  borrowed?: number
  available?: number
  inUse?: number
}

function GlobalStatusBar({ gatewayUrl }: Props) {
  const [cpuUsage, setCpuUsage] = useState(0)
  const [ramUsage, setRamUsage] = useState(0)
  const [ramTotal, setRamTotal] = useState(0)
  const [poolStats, setPoolStats] = useState<PoolStats | null>(null)
  const [loading, setLoading] = useState(true)

  const fetchStats = useCallback(async () => {
    try {
      const [impactRes, poolRes] = await Promise.allSettled([
        fetch(`${gatewayUrl}/api/v1/gateway-impact`, { signal: AbortSignal.timeout(5000) }),
        fetch(`${gatewayUrl}/api/v1/pool-stats`, { signal: AbortSignal.timeout(5000) }),
      ])

      if (impactRes.status === 'fulfilled' && impactRes.value.ok) {
        checkAuthResponse(impactRes.value)
        const raw = await impactRes.value.json()
        const data = raw.data || raw
        setCpuUsage(data.cpuUsagePercent ?? data.cpu_usage_percent ?? data.gatewayCpuPercent ?? 0)
        const MB = 1024 * 1024
        const usedBytes = data.memoryUsedBytes ?? data.memory_used_bytes ?? data.heapUsed ?? 0
        const usedFromMb = (data.memoryUsageMb ?? data.memory_usage_mb ?? data.gatewayMemoryMb ?? 0) * MB
        const maxBytes = data.memoryMaxBytes ?? data.memory_max_bytes ?? data.heapMax ?? 0
        const maxFromMb = (data.maxMemoryMb ?? 0) * MB
        setRamUsage(usedBytes || usedFromMb)
        setRamTotal(maxBytes || maxFromMb)
      }

      if (poolRes.status === 'fulfilled' && poolRes.value.ok) {
        checkAuthResponse(poolRes.value)
        const raw = await poolRes.value.json()
        const data = raw.data || raw
        setPoolStats({
          activeExecutors: data.activeExecutors ?? data.inUse ?? data.borrowed ?? 0,
          availableExecutors: data.availableExecutors ?? data.available ?? 0,
          poolSize: data.poolSize ?? 0,
        })
      }

      setLoading(false)
    } catch {
      setLoading(false)
    }
  }, [gatewayUrl])

  // Pause polling when tab is hidden (Sprint 3 perf P10).
  useVisibilityAwarePolling(fetchStats, 5000)

  const formatBytes = (bytes: number): string => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
  }

  const getUsageColor = (pct: number): string => {
    if (pct < 50) return 'var(--success)'
    if (pct < 75) return 'var(--warning)'
    return 'var(--error)'
  }

  const cpuPct = Math.min(100, cpuUsage)
  const ramPct = ramTotal > 0 ? Math.min(100, (ramUsage / ramTotal) * 100) : 0
  const poolActive = poolStats?.activeExecutors ?? 0
  const poolTotal = poolStats?.poolSize ?? 0
  const poolAvailable = poolStats?.availableExecutors ?? 0
  const poolPct = poolTotal > 0 ? Math.min(100, (poolActive / poolTotal) * 100) : 0

  if (loading) {
    return (
      <div className="global-status-bar">
        <div className="gsb-loading">
          <Loader size={11} className="spin" />
          <span>Loading...</span>
        </div>
      </div>
    )
  }

  return (
    <div className="global-status-bar">
      {/* Left: CPU + RAM */}
      <div className="gsb-left">
        <div className="gsb-metric" title={`CPU: ${cpuPct.toFixed(1)}%`}>
          <Cpu size={11} />
          <div className="gsb-bar">
            <div className="gsb-bar-fill" style={{ width: `${cpuPct}%`, background: getUsageColor(cpuPct) }} />
          </div>
          <span className="gsb-value">{cpuPct.toFixed(0)}%</span>
        </div>
        <div className="gsb-metric" title={`RAM: ${formatBytes(ramUsage)} / ${formatBytes(ramTotal)}`}>
          <MemoryStick size={11} />
          <div className="gsb-bar">
            <div className="gsb-bar-fill" style={{ width: `${ramPct}%`, background: getUsageColor(ramPct) }} />
          </div>
          <span className="gsb-value">{formatBytes(ramUsage)}</span>
        </div>
      </div>

      {/* Right: Pool stats */}
      <div className="gsb-right">
        {poolStats && poolTotal > 0 && (
          <div className="gsb-metric" title={`Pool: ${poolActive}/${poolTotal} active, ${poolAvailable} available`}>
            <span className="gsb-pool-label">Pool:</span>
            <span className="gsb-pool-count" style={{ color: getUsageColor(poolPct) }}>
              {poolActive}/{poolTotal}
            </span>
            <div className="gsb-bar">
              <div className="gsb-bar-fill" style={{ width: `${poolPct}%`, background: getUsageColor(poolPct) }} />
            </div>
            <span className="gsb-value">{poolAvailable} avail</span>
          </div>
        )}
      </div>
    </div>
  )
}

export default GlobalStatusBar
