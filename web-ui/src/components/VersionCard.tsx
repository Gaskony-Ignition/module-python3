import { Download, Trash2, CheckCircle, Loader } from 'lucide-react'

interface VersionCardProps {
  version: string
  status: 'installed' | 'available' | 'installing' | 'uninstalling'
  path?: string
  isDefault?: boolean
  poolSize?: number
  onInstall: () => void
  onUninstall: () => void
}

function VersionCard({
  version,
  status,
  path,
  isDefault,
  poolSize,
  onInstall,
  onUninstall,
}: VersionCardProps) {
  const isBusy = status === 'installing' || status === 'uninstalling'
  const isInstalled = status === 'installed'

  const statusLabel = {
    installed: 'Installed',
    available: 'Available',
    installing: 'Installing…',
    uninstalling: 'Removing…',
  }[status]

  const statusClass = {
    installed: 'version-badge--installed',
    available: 'version-badge--available',
    installing: 'version-badge--installing',
    uninstalling: 'version-badge--uninstalling',
  }[status]

  // Display as e.g. "Python 3.11" from "3.11.9" or "3.11"
  const displayLabel = version.startsWith('3') ? `Python ${version}` : version

  return (
    <div className={`version-card ${isInstalled ? 'version-card--installed' : ''}`}>
      <div className="version-card__header">
        <span className="version-card__label">{displayLabel}</span>
        <div className="version-card__badges">
          {isDefault && (
            <span className="version-badge version-badge--default">Default</span>
          )}
          <span className={`version-badge ${statusClass}`}>{statusLabel}</span>
        </div>
      </div>

      {path && (
        <div className="version-card__path" title={path}>
          <span className="version-card__path-text">{path}</span>
        </div>
      )}

      {isInstalled && poolSize !== undefined && (
        <div className="version-card__pool">
          <span className="version-card__pool-label">Pool size:</span>
          <span className="version-card__pool-value">{poolSize}</span>
        </div>
      )}

      <div className="version-card__actions">
        {isInstalled ? (
          <button
            className="version-card-btn version-card-btn--danger"
            onClick={onUninstall}
            disabled={isBusy}
            title={`Uninstall Python ${version}`}
          >
            {isBusy ? (
              <Loader size={14} className="version-card-btn__spinner" />
            ) : (
              <Trash2 size={14} />
            )}
            {isBusy ? 'Removing…' : 'Uninstall'}
          </button>
        ) : (
          <button
            className="version-card-btn version-card-btn--primary"
            onClick={onInstall}
            disabled={isBusy}
            title={`Install Python ${version}`}
          >
            {isBusy ? (
              <Loader size={14} className="version-card-btn__spinner" />
            ) : (
              <Download size={14} />
            )}
            {status === 'installing' ? 'Installing…' : 'Install'}
          </button>
        )}

        {isInstalled && !isBusy && (
          <span className="version-card__installed-icon" title="Installed">
            <CheckCircle size={14} />
          </span>
        )}
      </div>
    </div>
  )
}

export default VersionCard
