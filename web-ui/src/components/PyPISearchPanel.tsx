import { useState, useEffect, useRef, useCallback } from 'react'
import { Search, Loader, ExternalLink, Download, AlertCircle, ChevronDown, ChevronUp, User, Scale } from 'lucide-react'
import { apiGet } from '../utils/api'
import './PyPISearchPanel.css'

interface PyPIResult {
  name: string
  version: string
  summary: string
  author?: string
  license?: string
  homepage?: string
  versions?: string[]
}

interface SearchResponse {
  success: boolean
  query: string
  count: number
  results: PyPIResult[]
}

interface PyPIInfoResponse {
  success: boolean
  package?: PyPIResult
}

interface Props {
  onInstall: (packageName: string, version?: string) => void
  /** Name of the package currently being installed (if any), to show progress. */
  installing?: string
}

function PyPISearchPanel({ onInstall, installing }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<PyPIResult[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [searched, setSearched] = useState(false)
  const [expandedPkg, setExpandedPkg] = useState<string | null>(null)
  const [pkgDetails, setPkgDetails] = useState<Record<string, PyPIResult>>({})
  const [detailLoading, setDetailLoading] = useState<string | null>(null)
  const [selectedVersions, setSelectedVersions] = useState<Record<string, string>>({})
  const debounceRef = useRef<number | null>(null)

  const doSearch = useCallback(async (q: string) => {
    if (!q.trim()) {
      setResults([])
      setSearched(false)
      return
    }
    setLoading(true)
    setError(null)
    setSearched(true)
    try {
      const data = await apiGet<SearchResponse>(
        `/api/v1/packages/search-pypi?q=${encodeURIComponent(q.trim())}`
      )
      setResults(data.results || [])
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Search failed')
      setResults([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (!query.trim()) {
      setResults([])
      setSearched(false)
      return
    }
    debounceRef.current = window.setTimeout(() => doSearch(query), 500)
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current) }
  }, [query, doSearch])

  const handleToggleExpand = useCallback(async (pkgName: string) => {
    if (expandedPkg === pkgName) {
      setExpandedPkg(null)
      return
    }
    setExpandedPkg(pkgName)

    // Fetch details if not cached
    if (!pkgDetails[pkgName]) {
      setDetailLoading(pkgName)
      try {
        const data = await apiGet<PyPIInfoResponse>(
          `/api/v1/packages/pypi-info/${encodeURIComponent(pkgName)}`
        )
        if (data.success && data.package) {
          setPkgDetails(prev => ({ ...prev, [pkgName]: data.package! }))
          if (data.package.version) {
            setSelectedVersions(prev => ({ ...prev, [pkgName]: data.package!.version }))
          }
        }
      } catch {
        // Silently fail — details just won't show
      } finally {
        setDetailLoading(null)
      }
    }
  }, [expandedPkg, pkgDetails])

  return (
    <div className="pypi-search">
      <div className="pypi-search__input-wrap">
        <Search size={14} className="pypi-search__icon" />
        <input
          className="pypi-search__input"
          type="text"
          placeholder="Search PyPI packages..."
          value={query}
          onChange={e => setQuery(e.target.value)}
          spellCheck={false}
          autoFocus
        />
        {loading && <Loader size={14} className="pypi-search__spinner" />}
      </div>

      {error && (
        <div className="pypi-search__error">
          <AlertCircle size={14} />
          {error}
        </div>
      )}

      <div className="pypi-search__results">
        {!loading && searched && results.length === 0 && !error && (
          <div className="pypi-search__empty">
            No packages found for &quot;{query}&quot;
          </div>
        )}
        {results.map(pkg => {
          const isExpanded = expandedPkg === pkg.name
          const details = pkgDetails[pkg.name]
          const selVersion = selectedVersions[pkg.name] || pkg.version

          return (
            <div key={pkg.name} className={`pypi-search__result ${isExpanded ? 'pypi-search__result--expanded' : ''}`}>
              <div className="pypi-search__result-main">
                <div
                  className="pypi-search__result-info"
                  onClick={() => handleToggleExpand(pkg.name)}
                  style={{ cursor: 'pointer' }}
                >
                  <div className="pypi-search__result-header">
                    <span className="pypi-search__result-name">{pkg.name}</span>
                    <span className="pypi-search__result-version">{pkg.version}</span>
                    {isExpanded ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
                  </div>
                  {pkg.summary && (
                    <p className="pypi-search__result-summary">{pkg.summary}</p>
                  )}
                </div>
                <div className="pypi-search__result-actions">
                  <a
                    className="pypi-search__link"
                    href={`https://pypi.org/project/${encodeURIComponent(pkg.name)}/`}
                    target="_blank"
                    rel="noopener noreferrer"
                    title="View on PyPI"
                  >
                    <ExternalLink size={13} />
                  </a>
                  <button
                    className="pypi-search__install-btn"
                    onClick={() => onInstall(pkg.name, selVersion !== pkg.version ? selVersion : undefined)}
                    disabled={installing === pkg.name}
                    title={
                      installing === pkg.name
                        ? `Installing ${pkg.name}…`
                        : `Install ${pkg.name}${selVersion !== pkg.version ? `==${selVersion}` : ''}`
                    }
                  >
                    {installing === pkg.name ? (
                      <>
                        <Loader size={13} className="pypi-search__spinner" />
                        Installing…
                      </>
                    ) : (
                      <>
                        <Download size={13} />
                        Install
                      </>
                    )}
                  </button>
                </div>
              </div>

              {isExpanded && (
                <div className="pypi-search__detail">
                  {detailLoading === pkg.name ? (
                    <div className="pypi-search__detail-loading">
                      <Loader size={12} className="pypi-search__spinner" /> Loading metadata...
                    </div>
                  ) : details ? (
                    <>
                      <div className="pypi-search__detail-meta">
                        {details.author && (
                          <span className="pypi-search__detail-item">
                            <User size={11} /> {details.author}
                          </span>
                        )}
                        {details.license && (
                          <span className="pypi-search__detail-item">
                            <Scale size={11} /> {details.license}
                          </span>
                        )}
                        {details.homepage && (
                          <a
                            className="pypi-search__detail-link"
                            href={details.homepage}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            <ExternalLink size={11} /> Homepage
                          </a>
                        )}
                      </div>
                      {details.versions && details.versions.length > 0 && (
                        <div className="pypi-search__detail-versions">
                          <label className="pypi-search__version-label">Version:</label>
                          <select
                            className="pypi-search__version-select"
                            value={selVersion}
                            onChange={e => setSelectedVersions(prev => ({ ...prev, [pkg.name]: e.target.value }))}
                          >
                            {[...details.versions].reverse().slice(0, 50).map(v => (
                              <option key={v} value={v}>
                                {v}{v === details.version ? ' (latest)' : ''}
                              </option>
                            ))}
                          </select>
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="pypi-search__detail-meta">
                      <span className="pypi-search__detail-item">No additional metadata available</span>
                    </div>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default PyPISearchPanel
