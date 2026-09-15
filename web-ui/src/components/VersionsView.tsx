import { useState, useEffect, useCallback } from 'react'
import { RefreshCw, Package, AlertCircle, Loader } from 'lucide-react'
import { apiPost } from '../utils/api'
import PageHeader from './PageHeader'
import VersionCard from './VersionCard'
import './VersionsView.css'

interface VersionEntry {
  version: string
  status: 'installed' | 'available' | 'installing' | 'uninstalling'
  path?: string
  isDefault?: boolean
  poolSize?: number
}

interface Props {
  gatewayUrl: string
  showToast: (message: string, type: 'success' | 'error') => void
}

function VersionsView({ gatewayUrl, showToast }: Props) {
  const [versions, setVersions] = useState<VersionEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionInProgress, setActionInProgress] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  const fetchVersions = useCallback(async () => {
    try {
      // Fetch all three endpoints in parallel, fail gracefully for each
      const [distRes, versionsRes, poolRes] = await Promise.allSettled([
        fetch(`${gatewayUrl}/api/v1/distributions`),
        fetch(`${gatewayUrl}/api/v1/versions`),
        fetch(`${gatewayUrl}/api/v1/pool-stats`),
      ])

      // Parse distributions (full catalog with install status)
      let distList: VersionEntry[] = []
      if (distRes.status === 'fulfilled' && distRes.value.ok) {
        const raw = await distRes.value.json()
        const data = raw.data || raw
        const arr: unknown[] = Array.isArray(data) ? data : (data.distributions || data.versions || [])
        distList = (arr as Array<{
          version: string
          installed?: boolean
          available?: boolean
          path?: string
          isDefault?: boolean
        }>).map((d) => ({
          version: d.version,
          status: d.installed ? ('installed' as const) : ('available' as const),
          path: d.path,
          isDefault: d.isDefault ?? false,
        }))
      }

      // Parse active/installed versions
      let activeVersions: string[] = []
      if (versionsRes.status === 'fulfilled' && versionsRes.value.ok) {
        const raw = await versionsRes.value.json()
        const data = raw.data || raw
        activeVersions = Array.isArray(data)
          ? (data as string[])
          : (data.versions as string[]) || []
      }

      // Parse pool stats to enrich pool size
      let poolByVersion: Record<string, number> = {}
      if (poolRes.status === 'fulfilled' && poolRes.value.ok) {
        const raw = await poolRes.value.json()
        const data = raw.data || raw
        if (data.poolSize !== undefined) {
          // Single pool – attach to first installed version
          poolByVersion = { __single__: data.poolSize as number }
        } else if (data.pools && typeof data.pools === 'object') {
          poolByVersion = data.pools as Record<string, number>
        }
      }

      // If we got nothing from distributions endpoint, build list from versions
      if (distList.length === 0 && activeVersions.length > 0) {
        distList = activeVersions.map((v, i) => ({
          version: v,
          status: 'installed' as const,
          isDefault: i === 0,
        }))
      }

      // Merge active version info and pool sizes into dist list
      const merged: VersionEntry[] = distList.map((entry, i) => {
        const isActive = activeVersions.includes(entry.version)
        const poolSize =
          poolByVersion[entry.version] ??
          (poolByVersion['__single__'] !== undefined && i === 0
            ? poolByVersion['__single__']
            : undefined)
        return {
          ...entry,
          status: isActive ? 'installed' : entry.status,
          poolSize: entry.status === 'installed' || isActive ? poolSize : undefined,
        }
      })

      setVersions(merged)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load versions')
    }
  }, [gatewayUrl])

  // Initial load
  useEffect(() => {
    setLoading(true)
    fetchVersions().finally(() => setLoading(false))
  }, [fetchVersions])

  const handleRefresh = async () => {
    setRefreshing(true)
    await fetchVersions()
    setRefreshing(false)
  }

  const handleInstall = async (version: string) => {
    setActionInProgress(version)
    // Optimistic UI: mark as installing
    setVersions((prev) =>
      prev.map((v) => (v.version === version ? { ...v, status: 'installing' } : v))
    )
    try {
      const data = await apiPost<{ success?: boolean; error?: string; message?: string }>(
        '/api/v1/distributions/install', { version }, 120_000
      )
      if (data.success) {
        showToast('Python version installed successfully', 'success')
        await fetchVersions()
      } else {
        showToast(data.error || data.message || 'Installation failed', 'error')
        await fetchVersions()
      }
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Network error', 'error')
      await fetchVersions()
    } finally {
      setActionInProgress(null)
    }
  }

  const handleUninstall = async (version: string) => {
    if (!window.confirm(`Uninstall Python ${version}? This cannot be undone.`)) return
    setActionInProgress(version)
    // Optimistic UI: mark as uninstalling
    setVersions((prev) =>
      prev.map((v) => (v.version === version ? { ...v, status: 'uninstalling' } : v))
    )
    try {
      const data = await apiPost<{ success?: boolean; error?: string; message?: string }>(
        '/api/v1/distributions/uninstall', { version }, 120_000
      )
      if (data.success) {
        showToast('Python version uninstalled successfully', 'success')
        await fetchVersions()
      } else {
        showToast(data.error || data.message || 'Uninstall failed', 'error')
        await fetchVersions()
      }
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Network error', 'error')
      await fetchVersions()
    } finally {
      setActionInProgress(null)
    }
  }

  const installedCount = versions.filter((v) => v.status === 'installed').length

  return (
    <div className="versions-view">
      {/* Header */}
      <PageHeader icon={Package} title="Python Versions" subtitle={loading ? 'Loading...' : `${installedCount} installed · ${versions.length} total`}>
        <button className={`versions-refresh-btn ${refreshing ? 'spinning' : ''}`} onClick={handleRefresh} disabled={refreshing || loading} title="Refresh versions">
          <RefreshCw size={13} />
          Refresh
        </button>
      </PageHeader>

      {/* Body */}
      <div className="versions-view__body">
        {/* Error banner */}
        {error && (
          <div className="versions-error">
            <AlertCircle size={16} />
            {error}
          </div>
        )}

        {/* Loading state */}
        {loading ? (
          <div className="versions-loading">
            <Loader size={20} />
            Loading Python versions…
          </div>
        ) : versions.length === 0 ? (
          <div className="versions-empty">
            <Package size={48} />
            <span>No Python versions found.</span>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
              Click Refresh to reload or check gateway connectivity.
            </span>
          </div>
        ) : (
          <div className="versions-grid">
            {versions.map((v) => (
              <VersionCard
                key={v.version}
                version={v.version}
                status={
                  actionInProgress === v.version
                    ? v.status === 'installed' || v.status === 'uninstalling'
                      ? 'uninstalling'
                      : 'installing'
                    : v.status
                }
                path={v.path}
                isDefault={v.isDefault}
                poolSize={v.poolSize}
                onInstall={() => handleInstall(v.version)}
                onUninstall={() => handleUninstall(v.version)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

export default VersionsView
