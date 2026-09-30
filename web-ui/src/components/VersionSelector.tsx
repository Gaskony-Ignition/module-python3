import { useState, useEffect } from 'react'
import { Code2 } from 'lucide-react'
import { apiGet } from '../utils/api'

interface DistributionEntry {
  version?: string
  installed?: boolean
  [key: string]: unknown
}

interface DistributionsResponse {
  distributions?: DistributionEntry[]
  [key: string]: unknown
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
        const data = await apiGet<DistributionsResponse>('/api/v1/distributions', 6000)

        let list: string[] = []
        if (Array.isArray(data?.distributions)) {
          list = data.distributions
            .filter((d: DistributionEntry) => d.installed === true)
            .map((d: DistributionEntry) => d.version ?? String(d))
            .filter(Boolean)
        }

        if (!cancelled) {
          setVersions(list)
          // Auto-select first if nothing selected yet
          if (list.length > 0 && !selectedVersion) {
            onVersionChange(list[0])
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
