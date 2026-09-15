import { useState, useEffect, useCallback } from 'react'
import ScriptTreePanel from './ScriptTreePanel'
import ScriptDetailPanel from './ScriptDetailPanel'
import ImportExportModal from './ImportExportModal'
import { Plus, FolderPlus, Upload, RefreshCw, ScrollText } from 'lucide-react'
import { apiPost, apiDelete } from '../utils/api'
import PageHeader from './PageHeader'
import './ScriptsView.css'

export interface ScriptEntry {
  name: string
  description?: string
  updatedAt?: string
  folderPath?: string
}

interface Props {
  gatewayUrl: string
  showToast: (message: string, type: 'success' | 'error') => void
}

function ScriptsView({ gatewayUrl, showToast }: Props) {
  const [scripts, setScripts] = useState<ScriptEntry[]>([])
  const [selectedScript, setSelectedScript] = useState<string | null>(null)
  const [showImportModal, setShowImportModal] = useState<boolean>(false)
  const [loading, setLoading] = useState<boolean>(false)
  const [error, setError] = useState<string>('')
  // Local folder state: extra folders created by user that may not have scripts yet
  const [localFolders, setLocalFolders] = useState<string[]>([])
  const [selectedFolder, setSelectedFolder] = useState<string | null>(null)

  const fetchScripts = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const res = await fetch(`${gatewayUrl}/api/v1/scripts/list`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const raw = await res.json()
      const data = raw.data || raw
      // data could be an array or { scripts: [...] }
      const list: ScriptEntry[] = Array.isArray(data)
        ? data
        : Array.isArray(data.scripts)
          ? data.scripts
          : []
      setScripts(list)
    } catch (err) {
      console.error('Failed to fetch scripts:', err)
      setError(`Failed to load scripts: ${err}`)
    } finally {
      setLoading(false)
    }
  }, [gatewayUrl])

  useEffect(() => {
    fetchScripts()
  }, [fetchScripts])

  const handleNewScript = async () => {
    const name = window.prompt('Enter script name:')
    if (!name || !name.trim()) return
    const trimmed = name.trim()

    try {
      await apiPost('/api/v1/scripts/save', {
        name: trimmed,
        code: '',
        description: '',
        folderPath: selectedFolder || undefined,
      })
      await fetchScripts()
      setSelectedScript(trimmed)
      showToast('Script created successfully', 'success')
    } catch (err) {
      showToast(`Failed to create script: ${err}`, 'error')
    }
  }

  const handleNewFolder = () => {
    const folderName = window.prompt('Enter folder name:')
    if (!folderName || !folderName.trim()) return
    const trimmed = folderName.trim()
    // Derive existing folders from scripts + localFolders
    const scriptFolders = scripts
      .map(s => s.folderPath)
      .filter((f): f is string => !!f)
    const allFolders = [...new Set([...scriptFolders, ...localFolders])]
    if (allFolders.includes(trimmed)) {
      showToast(`Folder "${trimmed}" already exists.`, 'error')
      return
    }
    setLocalFolders(prev => [...prev, trimmed])
  }

  const handleDeleteScript = async (name: string) => {
    try {
      await apiDelete(`/api/v1/scripts/delete/${encodeURIComponent(name)}`)
      if (selectedScript === name) {
        setSelectedScript(null)
      }
      await fetchScripts()
      showToast('Script deleted successfully', 'success')
    } catch (err) {
      showToast(`Failed to delete script: ${err}`, 'error')
    }
  }

  const handleRenameScript = async (oldName: string, newName: string) => {
    if (selectedScript === oldName) {
      setSelectedScript(newName)
    }
    await fetchScripts()
  }

  // Merge script-derived folders with localFolders, deduplicated
  const scriptFolders = scripts
    .map(s => s.folderPath)
    .filter((f): f is string => !!f)
  const allFolders = [...new Set([...scriptFolders, ...localFolders])]

  return (
    <div className="scripts-view">
      {/* Header */}
      <PageHeader icon={ScrollText} title="Scripts" subtitle="Manage and organise Python scripts">
        <button className="scripts-header-btn" onClick={fetchScripts} disabled={loading} title="Refresh"><RefreshCw size={14} /><span>Refresh</span></button>
        <button className="scripts-header-btn" onClick={() => setShowImportModal(true)} title="Import .py file"><Upload size={14} /><span>Import .py</span></button>
        <button className="scripts-header-btn" onClick={handleNewFolder} title="New Folder"><FolderPlus size={14} /><span>New Folder</span></button>
        <button className="scripts-header-btn primary" onClick={handleNewScript} title="New Script"><Plus size={14} /><span>New Script</span></button>
      </PageHeader>

      {/* Error banner */}
      {error && (
        <div className="scripts-error-banner">{error}</div>
      )}

      {/* Two-panel layout */}
      <div className="scripts-panels">
        <div className="scripts-left-panel">
          <ScriptTreePanel
            scripts={scripts}
            folders={allFolders}
            selectedScript={selectedScript}
            onSelectScript={setSelectedScript}
            onDeleteScript={handleDeleteScript}
            onRenameScript={handleRenameScript}
            onRefresh={fetchScripts}
            gatewayUrl={gatewayUrl}
            onSelectFolder={setSelectedFolder}
            showToast={showToast}
          />
        </div>
        <div className="scripts-right-panel">
          <ScriptDetailPanel
            gatewayUrl={gatewayUrl}
            scriptName={selectedScript}
            onScriptSaved={fetchScripts}
            onScriptRenamed={(oldName, newName) => handleRenameScript(oldName, newName)}
          />
        </div>
      </div>

      {/* Import Modal */}
      <ImportExportModal
        isOpen={showImportModal}
        onClose={() => setShowImportModal(false)}
        onImported={() => {
          setShowImportModal(false)
          fetchScripts()
        }}
      />
    </div>
  )
}

export default ScriptsView
