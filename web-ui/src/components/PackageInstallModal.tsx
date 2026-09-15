import { useState, useEffect, useRef, useCallback } from 'react'
import { X, Download, Loader, CheckCircle, AlertCircle } from 'lucide-react'

interface PyPIInfo {
  name: string
  version: string
  summary: string
  author: string
  homePage: string
  license: string
}

interface PackageInstallModalProps {
  isOpen: boolean
  onClose: () => void
  onInstall: (packageName: string, version?: string) => void
}

async function lookupPyPI(packageName: string): Promise<PyPIInfo | null> {
  if (!packageName.trim()) return null
  try {
    const res = await fetch(
      `https://pypi.org/pypi/${encodeURIComponent(packageName.trim())}/json`,
      { signal: AbortSignal.timeout(8000) }
    )
    if (!res.ok) return null
    const data = await res.json()
    const info = data.info
    return {
      name: info.name,
      version: info.version,
      summary: info.summary || '',
      author: info.author || '',
      homePage: info.home_page || info.project_url || '',
      license: info.license || '',
    }
  } catch {
    return null
  }
}

function PackageInstallModal({ isOpen, onClose, onInstall }: PackageInstallModalProps) {
  const [packageName, setPackageName] = useState('')
  const [versionConstraint, setVersionConstraint] = useState('')
  const [pypiInfo, setPypiInfo] = useState<PyPIInfo | null>(null)
  const [pypiState, setPypiState] = useState<'idle' | 'checking' | 'found' | 'not_found'>('idle')
  const nameInputRef = useRef<HTMLInputElement>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Focus name input when modal opens; reset fields on close
  useEffect(() => {
    if (isOpen) {
      setPackageName('')
      setVersionConstraint('')
      setPypiInfo(null)
      setPypiState('idle')
      setTimeout(() => nameInputRef.current?.focus(), 50)
    }
  }, [isOpen])

  const triggerPyPILookup = useCallback((name: string) => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    const trimmed = name.trim()
    if (!trimmed) {
      setPypiInfo(null)
      setPypiState('idle')
      return
    }
    setPypiState('checking')
    debounceRef.current = setTimeout(async () => {
      const info = await lookupPyPI(trimmed)
      if (info) {
        setPypiInfo(info)
        setPypiState('found')
      } else {
        setPypiInfo(null)
        setPypiState('not_found')
      }
    }, 500)
  }, [])

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value
    setPackageName(val)
    triggerPyPILookup(val)
  }

  if (!isOpen) return null

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const trimmed = packageName.trim()
    if (!trimmed) return
    const ver = versionConstraint.trim() || undefined
    onInstall(trimmed, ver)
    onClose()
  }

  const handleBackdropClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) onClose()
  }

  // When the user clicks "Use latest", populate the version constraint with ==latest
  const handleUseLatest = () => {
    if (pypiInfo) {
      setVersionConstraint(`==${pypiInfo.version}`)
    }
  }

  return (
    <div className="pkg-modal-backdrop" onClick={handleBackdropClick}>
      <div className="pkg-modal" role="dialog" aria-modal="true" aria-labelledby="pkg-modal-title">
        {/* Header */}
        <div className="pkg-modal__header">
          <h3 id="pkg-modal-title" className="pkg-modal__title">
            <Download size={16} />
            Install Package
          </h3>
          <button className="pkg-modal__close" onClick={onClose} title="Close">
            <X size={16} />
          </button>
        </div>

        {/* Form */}
        <form className="pkg-modal__body" onSubmit={handleSubmit}>
          <div className="pkg-modal__field">
            <label className="pkg-modal__label" htmlFor="pkg-name-input">
              Package name
            </label>
            <div className="pkg-modal__input-row">
              <input
                id="pkg-name-input"
                ref={nameInputRef}
                className="pkg-modal__input"
                type="text"
                placeholder="e.g. requests"
                value={packageName}
                onChange={handleNameChange}
                autoComplete="off"
                spellCheck={false}
              />
              {pypiState === 'checking' && (
                <span className="pkg-modal__pypi-status pkg-modal__pypi-status--checking">
                  <Loader size={13} className="pkg-btn__spinner" />
                </span>
              )}
              {pypiState === 'found' && (
                <span className="pkg-modal__pypi-status pkg-modal__pypi-status--found">
                  <CheckCircle size={13} />
                </span>
              )}
              {pypiState === 'not_found' && packageName.trim() && (
                <span className="pkg-modal__pypi-status pkg-modal__pypi-status--notfound">
                  <AlertCircle size={13} />
                </span>
              )}
            </div>
          </div>

          {/* PyPI info block */}
          {pypiState === 'found' && pypiInfo && (
            <div className="pkg-modal__pypi-info">
              <div className="pkg-modal__pypi-info-header">
                <span className="pkg-badge pkg-badge--pypi">PyPI</span>
                <span className="pkg-modal__pypi-name">{pypiInfo.name}</span>
                <button
                  type="button"
                  className="pkg-modal__pypi-latest-btn"
                  onClick={handleUseLatest}
                  title={`Pin to version ${pypiInfo.version}`}
                >
                  v{pypiInfo.version}
                </button>
              </div>
              {pypiInfo.summary && (
                <p className="pkg-modal__pypi-summary">{pypiInfo.summary}</p>
              )}
              <div className="pkg-modal__pypi-meta">
                {pypiInfo.author && (
                  <span className="pkg-modal__pypi-meta-item">
                    {pypiInfo.author}
                  </span>
                )}
                {pypiInfo.license && (
                  <span className="pkg-modal__pypi-meta-item pkg-modal__pypi-meta-item--muted">
                    {pypiInfo.license}
                  </span>
                )}
              </div>
            </div>
          )}

          {pypiState === 'not_found' && packageName.trim() && (
            <div className="pkg-modal__pypi-notfound">
              <AlertCircle size={13} />
              Package not found on PyPI. You can still try to install it.
            </div>
          )}

          <div className="pkg-modal__field">
            <label className="pkg-modal__label" htmlFor="pkg-version-input">
              Version constraint{' '}
              <span className="pkg-modal__label-hint">(optional)</span>
            </label>
            <input
              id="pkg-version-input"
              className="pkg-modal__input"
              type="text"
              placeholder="e.g. >=1.0.0 or ==2.28.0"
              value={versionConstraint}
              onChange={(e) => setVersionConstraint(e.target.value)}
              autoComplete="off"
              spellCheck={false}
            />
          </div>

          <div className="pkg-modal__preview">
            {packageName.trim()
              ? `pip install "${packageName.trim()}${versionConstraint.trim() ? versionConstraint.trim() : ''}"`
              : 'Enter a package name above'}
          </div>

          <div className="pkg-modal__footer">
            <button type="button" className="pkg-modal__btn pkg-modal__btn--cancel" onClick={onClose}>
              Cancel
            </button>
            <button
              type="submit"
              className="pkg-modal__btn pkg-modal__btn--install"
              disabled={!packageName.trim()}
            >
              <Download size={13} />
              Install
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default PackageInstallModal
