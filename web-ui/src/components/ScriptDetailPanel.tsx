import { useState, useEffect } from 'react'
import { Edit2, Save, Download, Copy } from 'lucide-react'
import { apiGet, apiPost, apiDelete } from '../utils/api'

interface ScriptDetailPanelProps {
  gatewayUrl: string
  scriptName: string | null
  onScriptSaved: () => void
  onScriptRenamed?: (oldName: string, newName: string) => void
}

interface ScriptData {
  name: string
  code: string
  description: string
  folderPath?: string
  updatedAt?: string
}

function ScriptDetailPanel({
  gatewayUrl,
  scriptName,
  onScriptSaved,
  onScriptRenamed,
}: ScriptDetailPanelProps) {
  const [script, setScript] = useState<ScriptData | null>(null)
  const [isEditing, setIsEditing] = useState<boolean>(false)
  const [editName, setEditName] = useState<string>('')
  const [editCode, setEditCode] = useState<string>('')
  const [editDescription, setEditDescription] = useState<string>('')
  const [loading, setLoading] = useState<boolean>(false)
  const [saving, setSaving] = useState<boolean>(false)
  const [error, setError] = useState<string>('')
  const [copyMsg, setCopyMsg] = useState<string>('')

  useEffect(() => {
    if (!scriptName) {
      setScript(null)
      setIsEditing(false)
      return
    }

    setLoading(true)
    setError('')
    setIsEditing(false)

    apiGet<Record<string, unknown>>(`/api/v1/scripts/load/${encodeURIComponent(scriptName)}`)
      .then(raw => {
        // Handle both flat and nested response formats
        const data = (raw.script || raw) as Record<string, unknown>
        const loaded: ScriptData = {
          name: (data.name as string) || scriptName,
          code: (data.code as string) || '',
          description: (data.description as string) || '',
          folderPath: (data.folderPath as string) || undefined,
          updatedAt: (data.updatedAt as string) || (data.lastModified as string) || '',
        }
        setScript(loaded)
        setEditCode(loaded.code)
        setEditDescription(loaded.description)
        setEditName(loaded.name)
      })
      .catch(err => {
        console.error('Failed to load script:', err)
        setError(`Failed to load script: ${err}`)
      })
      .finally(() => setLoading(false))
  }, [gatewayUrl, scriptName])

  const handleEdit = () => {
    if (!script) return
    setEditCode(script.code)
    setEditDescription(script.description)
    setEditName(script.name)
    setIsEditing(true)
  }

  const handleSave = async () => {
    if (!script) return
    setSaving(true)
    setError('')

    const nameChanged = editName.trim() && editName.trim() !== script.name
    const newName = editName.trim() || script.name

    try {
      const saveResult = await apiPost<Record<string, unknown>>('/api/v1/scripts/save', {
        name: newName,
        code: editCode,
        description: editDescription,
        folderPath: script.folderPath || undefined,
      })

      // Verify save succeeded before deleting old entry
      if (saveResult.success === false) {
        throw new Error(String(saveResult.error || saveResult.message || 'Save failed'))
      }

      // If name changed, delete the old script entry only after confirmed save
      if (nameChanged) {
        await apiDelete(`/api/v1/scripts/delete/${encodeURIComponent(script.name)}`)
      }

      setScript(prev =>
        prev
          ? {
              ...prev,
              name: newName,
              code: editCode,
              description: editDescription,
            }
          : null
      )
      setIsEditing(false)

      if (nameChanged && onScriptRenamed) {
        onScriptRenamed(script.name, newName)
      } else {
        onScriptSaved()
      }
    } catch (err) {
      setError(`Failed to save: ${err}`)
      onScriptSaved() // Refresh list to restore correct state
    } finally {
      setSaving(false)
    }
  }

  const handleCancelEdit = () => {
    setIsEditing(false)
    if (script) {
      setEditCode(script.code)
      setEditDescription(script.description)
      setEditName(script.name)
    }
  }

  const handleExport = () => {
    if (!script) return
    const blob = new Blob([script.code], { type: 'text/x-python' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${script.name}.py`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const handleCopy = () => {
    if (!script) return
    navigator.clipboard.writeText(script.code).then(() => {
      setCopyMsg('Copied!')
      setTimeout(() => setCopyMsg(''), 2000)
    }).catch(() => {
      setCopyMsg('Failed')
      setTimeout(() => setCopyMsg(''), 2000)
    })
  }

  if (!scriptName) {
    return (
      <div className="script-detail-empty">
        <p>Select a script from the list to view its details.</p>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="script-detail-empty">
        <p>Loading {scriptName}...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="script-detail-empty">
        <p className="script-detail-error">{error}</p>
      </div>
    )
  }

  if (!script) {
    return (
      <div className="script-detail-empty">
        <p>Script not found.</p>
      </div>
    )
  }

  return (
    <div className="script-detail-panel">
      {/* Header */}
      <div className="script-detail-header">
        <div className="script-detail-meta">
          {isEditing ? (
            <input
              type="text"
              className="script-detail-name-input"
              value={editName}
              onChange={e => setEditName(e.target.value)}
              placeholder="Script name"
              aria-label="Script name"
            />
          ) : (
            <h2 className="script-detail-name">{script.name}</h2>
          )}
          {script.updatedAt && !isEditing && (
            <span className="script-detail-updated">
              Last modified: {new Date(script.updatedAt).toLocaleString()}
            </span>
          )}
          {script.folderPath && !isEditing && (
            <span className="script-detail-folder">
              Folder: {script.folderPath}
            </span>
          )}
        </div>
        <div className="script-detail-actions">
          {!isEditing && (
            <>
              <button
                className="script-action-btn"
                onClick={handleCopy}
                title="Copy code"
              >
                <Copy size={14} />
                <span>{copyMsg || 'Copy'}</span>
              </button>
              <button
                className="script-action-btn"
                onClick={handleExport}
                title="Export as .py"
              >
                <Download size={14} />
                <span>Export</span>
              </button>
              <button
                className="script-action-btn primary"
                onClick={handleEdit}
                title="Edit script"
              >
                <Edit2 size={14} />
                <span>Edit</span>
              </button>
            </>
          )}
          {isEditing && (
            <>
              <button
                className="script-action-btn"
                onClick={handleCancelEdit}
                title="Cancel editing"
              >
                Cancel
              </button>
              <button
                className="script-action-btn primary"
                onClick={handleSave}
                disabled={saving}
                title="Save script"
              >
                <Save size={14} />
                <span>{saving ? 'Saving...' : 'Save'}</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* Description */}
      <div className="script-detail-description-row">
        <label className="script-detail-label">Description</label>
        {isEditing ? (
          <input
            type="text"
            className="script-detail-desc-input"
            value={editDescription}
            onChange={e => setEditDescription(e.target.value)}
            placeholder="Optional description..."
          />
        ) : (
          <span className="script-detail-desc-text">
            {script.description || <em>No description</em>}
          </span>
        )}
      </div>

      {/* Code area */}
      <div className="script-detail-code-area">
        {isEditing ? (
          <textarea
            className="script-detail-editor"
            value={editCode}
            onChange={e => setEditCode(e.target.value)}
            spellCheck={false}
            autoComplete="off"
            autoCorrect="off"
            autoCapitalize="off"
          />
        ) : (
          <pre className="script-detail-code-block">
            <code>{script.code || '# (empty script)'}</code>
          </pre>
        )}
      </div>
    </div>
  )
}

export default ScriptDetailPanel
