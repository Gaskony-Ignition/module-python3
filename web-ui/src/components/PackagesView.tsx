import { useState, useEffect, useCallback } from 'react'
import { RefreshCw, Search, Plus, ShieldCheck, AlertCircle, Loader, Package } from 'lucide-react'
import { apiGet, apiPost } from '../utils/api'
import PageHeader from './PageHeader'
import PackageCard from './PackageCard'
import PackageInstallModal from './PackageInstallModal'
import PyPISearchPanel from './PyPISearchPanel'
import './PackagesView.css'

interface PackageEntry {
  name: string
  version?: string
  status: 'installed' | 'not_installed' | 'installing' | 'uninstalling'
  description?: string
}

interface CatalogPackage {
  name: string
  version?: string
  installed?: boolean
  status?: string
  description?: string
  summary?: string
}

interface InstallResult {
  success?: boolean
  error?: string
  message?: string
}

interface Props {
  gatewayUrl: string
  showToast: (message: string, type: 'success' | 'error' | 'info') => void
}

function PackagesView({ gatewayUrl: _gatewayUrl, showToast }: Props) {
  const [packages, setPackages] = useState<PackageEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [showInstallModal, setShowInstallModal] = useState(false)
  const [actionInProgress, setActionInProgress] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [activeTab, setActiveTab] = useState<'installed' | 'search'>('installed')

  const fetchPackages = useCallback(async () => {
    try {
      const raw = await apiGet<Record<string, unknown>>('/api/v1/packages/catalog')

      // The backend returns packages as either:
      // 1. An object map: { packages: { "numpy": {...}, "pandas": {...} } }
      // 2. An array: { packages: [{name: "numpy", ...}] }
      // Handle both formats gracefully
      const packagesField = (raw as Record<string, unknown>).packages ||
        (raw as Record<string, unknown>).catalog
      let arr: CatalogPackage[]

      if (Array.isArray(packagesField)) {
        arr = packagesField as CatalogPackage[]
      } else if (packagesField && typeof packagesField === 'object') {
        // Convert object map to array: { "numpy": {version: "1.0"} } -> [{name: "numpy", version: "1.0"}]
        arr = Object.entries(packagesField as Record<string, Record<string, unknown>>).map(
          ([name, info]) => ({
            name,
            version: (info.version as string) || undefined,
            installed: (info.installed as boolean) || undefined,
            status: info.status as string | undefined,
            description: (info.description as string) || (info.summary as string) || undefined,
            summary: info.summary as string | undefined,
          })
        )
      } else if (Array.isArray(raw)) {
        arr = raw as CatalogPackage[]
      } else {
        arr = []
      }

      const entries: PackageEntry[] = arr.map((p) => ({
        name: p.name,
        version: p.version,
        status: p.installed || p.status === 'installed' ? 'installed' : 'not_installed',
        description: p.description || p.summary,
      }))

      setPackages(entries)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load packages')
    }
  }, [])

  // Initial load
  useEffect(() => {
    setLoading(true)
    fetchPackages().finally(() => setLoading(false))
  }, [fetchPackages])

  const handleRefresh = async () => {
    setRefreshing(true)
    await fetchPackages()
    setRefreshing(false)
  }

  const handleInstall = async (packageName: string, version?: string) => {
    const fullName = version ? `${packageName}==${version}` : packageName
    setActionInProgress(packageName)
    // Long-running installs (e.g. pandas, numpy) can take minutes — let the user
    // know straight away rather than leaving the Search tab looking dead.
    showToast(`Installing ${packageName}… this can take a few minutes`, 'info')
    // Optimistic UI - add or update the package entry
    setPackages((prev) => {
      const exists = prev.some((p) => p.name === packageName)
      if (exists) {
        return prev.map((p) => (p.name === packageName ? { ...p, status: 'installing' } : p))
      }
      return [...prev, { name: packageName, status: 'installing' }]
    })
    try {
      const data = await apiPost<InstallResult>(
        `/api/v1/packages/install/${encodeURIComponent(fullName)}`,
        undefined,
        300000  // 5 minute timeout for pip install (large packages like numpy)
      )
      if (data.success === false) {
        showToast(data.error || data.message || 'Installation failed', 'error')
      } else {
        showToast('Package installed successfully', 'success')
      }
      await fetchPackages()
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Network error', 'error')
      await fetchPackages()
    } finally {
      setActionInProgress(null)
    }
  }

  const handleUninstall = async (packageName: string) => {
    if (!window.confirm(`Uninstall package "${packageName}"?`)) return
    setActionInProgress(packageName)
    setPackages((prev) =>
      prev.map((p) => (p.name === packageName ? { ...p, status: 'uninstalling' } : p))
    )
    try {
      const data = await apiPost<InstallResult>(
        `/api/v1/packages/uninstall/${encodeURIComponent(packageName)}`
      )
      if (data.success === false) {
        showToast(data.error || data.message || 'Uninstall failed', 'error')
      } else {
        showToast('Package uninstalled successfully', 'success')
      }
      await fetchPackages()
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Network error', 'error')
      await fetchPackages()
    } finally {
      setActionInProgress(null)
    }
  }

  const handleVerifyAll = async () => {
    setVerifying(true)
    try {
      const data = await apiPost<InstallResult>('/api/v1/packages/verify')
      if (data.success === false) {
        showToast(data.error || data.message || 'Verification failed', 'error')
      } else {
        showToast('All packages verified successfully', 'success')
      }
      await fetchPackages()
    } catch {
      // Verification endpoint may not exist; silently refresh
      await fetchPackages()
    } finally {
      setVerifying(false)
    }
  }

  // Filter by search query
  const filtered = packages.filter((p) => {
    if (!searchQuery.trim()) return true
    const q = searchQuery.toLowerCase()
    return (
      p.name.toLowerCase().includes(q) ||
      (p.description || '').toLowerCase().includes(q) ||
      (p.version || '').toLowerCase().includes(q)
    )
  })

  const installedCount = packages.filter((p) => p.status === 'installed').length

  return (
    <div className="packages-view">
      {/* Header */}
      <PageHeader icon={Package} title="Packages" subtitle={loading ? 'Loading...' : `${installedCount} installed · ${packages.length} total`}>
        <button className="pkg-action-btn pkg-action-btn--secondary" onClick={handleVerifyAll} disabled={verifying || loading} title="Verify all installed packages">
          {verifying ? <Loader size={13} className="pkg-action-btn__spinner" /> : <ShieldCheck size={13} />}
          Verify All
        </button>
        <button className="pkg-action-btn pkg-action-btn--primary" onClick={() => setShowInstallModal(true)} title="Install a package from PyPI">
          <Plus size={13} />
          Install from PyPI
        </button>
        <button className={`pkg-action-btn pkg-action-btn--secondary ${refreshing ? 'spinning' : ''}`} onClick={handleRefresh} disabled={refreshing || loading} title="Refresh packages">
          <RefreshCw size={13} />
        </button>
      </PageHeader>

      {/* Tab bar */}
      <div className="packages-view__tabs">
        <button
          className={`packages-view__tab ${activeTab === 'installed' ? 'packages-view__tab--active' : ''}`}
          onClick={() => setActiveTab('installed')}
        >
          Installed
        </button>
        <button
          className={`packages-view__tab ${activeTab === 'search' ? 'packages-view__tab--active' : ''}`}
          onClick={() => setActiveTab('search')}
        >
          Search PyPI
        </button>
      </div>

      {activeTab === 'search' ? (
        <PyPISearchPanel onInstall={handleInstall} installing={actionInProgress ?? undefined} />
      ) : (
        <>
          {/* Search bar */}
          <div className="packages-view__search-bar">
            <div className="packages-search-field">
              <Search size={14} className="packages-search-field__icon" />
              <input
                className="packages-search-field__input"
                type="text"
                placeholder="Search installed packages…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                spellCheck={false}
              />
            </div>
          </div>

          {/* Body */}
          <div className="packages-view__body">
            {/* Error banner */}
            {error && (
              <div className="packages-error">
                <AlertCircle size={16} />
                {error}
              </div>
            )}

            {/* Loading state */}
            {loading ? (
              <div className="packages-loading">
                <Loader size={20} />
                Loading packages…
              </div>
            ) : filtered.length === 0 ? (
              <div className="packages-empty">
                <Package size={40} />
                <span>
                  {searchQuery ? `No packages match "${searchQuery}"` : 'No packages found.'}
                </span>
              </div>
            ) : (
              <div className="packages-list">
                {filtered.map((pkg) => (
                  <PackageCard
                    key={pkg.name}
                    name={pkg.name}
                    version={pkg.version}
                    status={
                      actionInProgress === pkg.name
                        ? pkg.status === 'installed' || pkg.status === 'uninstalling'
                          ? 'uninstalling'
                          : 'installing'
                        : pkg.status
                    }
                    description={pkg.description}
                    onInstall={() => handleInstall(pkg.name)}
                    onUninstall={() => handleUninstall(pkg.name)}
                  />
                ))}
              </div>
            )}
          </div>
        </>
      )}

      {/* Install from PyPI modal */}
      <PackageInstallModal
        isOpen={showInstallModal}
        onClose={() => setShowInstallModal(false)}
        onInstall={handleInstall}
      />
    </div>
  )
}

export default PackagesView
