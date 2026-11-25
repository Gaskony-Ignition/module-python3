import { useState, useEffect } from 'react'
import { Code2 } from 'lucide-react'
import { apiGet } from '../utils/api'

// /versions lists the versions with a running pool, the bundled default included;
// /distributions marks only add-on installs as installed.
interface VersionsResponse {
  versions?: string[]
  default?: string
}

interface Props {
  gatewayUrl: string
  selectedVersion: string
  onVersionChange: (v: string) => void
}

function VersionSelector({ gatewayUrl: _gatewayUrl, selectedVersion, onVersionChange }: Props) {
  const [versions, setVersions] = useState<string[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    const load = async () => {
      try {
        const data = await apiGet<VersionsResponse>('/api/v1/versions', 6000)
        const list = Array.isArray(data?.versions) ? data.versions.filter(Boolean) : []

        if (!cancelled) {
          setVersions(list)
          if (list.length > 0 && !selectedVersion) {
            onVersionChange(data?.default && list.includes(data.default) ? data.default : list[0])
          }
        }
      } catch {
        // ignore network errors
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => { cancelled = true }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (loading) {
    return (
      <div className="version-selector version-selector--loading" title="Loading Python versions...">
        <Code2 size={13} />
        <span className="version-selector__label">Loading...</span>
      </div>
    )
  }

  if (versions.length === 0) {
    return (
      <div className="version-selector version-selector--empty" title="No Python versions found">
        <Code2 size={13} />
        <span className="version-selector__label">Default Python</span>
      </div>
    )
  }

  return (
    <div className="version-selector">
      <Code2 size={13} className="version-selector__icon" />
      <select
        className="version-selector__select"
        value={selectedVersion}
        onChange={e => onVersionChange(e.target.value)}
        title="Select Python version"
        aria-label="Select Python version"
      >
        {versions.map(v => (
          <option key={v} value={v}>{v}</option>
        ))}
      </select>
    </div>
  )
}

export default VersionSelector
