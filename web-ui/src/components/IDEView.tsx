import { useState, useRef, useCallback, useEffect } from 'react'
import CodeEditor from './CodeEditor'
import OutputPanel from './OutputPanel'
import ExecutionToolbar from './ExecutionToolbar'
import VersionSelector from './VersionSelector'
import { apiGet, apiPost } from '../utils/api'
import './IDEView.css'

interface Props {
  gatewayUrl: string
}

interface ScriptEntry {
  name: string
  description?: string
  folderPath?: string
}

interface ScriptLoadData {
  name: string
  code: string
  description: string
  folderPath?: string
}

const DEFAULT_CODE = `# Create or load a script to get started\n`

const LAST_SCRIPT_KEY = 'python3-ide-last-script'

const DEFAULT_OUTPUT_SIZE = 220
const SPLIT_DIR_KEY = 'python3-ide-split-dir'

type SplitDirection = 'vertical' | 'horizontal'

function IDEView({ gatewayUrl }: Props) {
  const [code, setCode] = useState(DEFAULT_CODE)
  const [output, setOutput] = useState('')
  const [error, setError] = useState('')
  const [executionTime, setExecutionTime] = useState<number | null>(null)
  const [isExecuting, setIsExecuting] = useState(false)
  const [selectedVersion, setSelectedVersion] = useState('')

  // Script loading state
  const [scriptList, setScriptList] = useState<ScriptEntry[]>([])
  const [showScriptMenu, setShowScriptMenu] = useState(false)
  const [loadedScriptName, setLoadedScriptName] = useState<string | null>(null)
  const scriptMenuRef = useRef<HTMLDivElement>(null)

  // Split direction state
  const [splitDirection, setSplitDirection] = useState<SplitDirection>(() => {
    return (localStorage.getItem(SPLIT_DIR_KEY) as SplitDirection) || 'vertical'
  })

  // Resize state (works for both vertical height and horizontal width)
  const [outputSize, setOutputSize] = useState(DEFAULT_OUTPUT_SIZE)
  const isDragging = useRef(false)
  const dragStart = useRef(0)
  const dragStartSize = useRef(DEFAULT_OUTPUT_SIZE)
  const containerRef = useRef<HTMLDivElement>(null)

  // Auto-load last script from localStorage on mount
  useEffect(() => {
    const lastScript = localStorage.getItem(LAST_SCRIPT_KEY)
    if (lastScript) {
      apiGet<Record<string, unknown>>(`/api/v1/scripts/load/${encodeURIComponent(lastScript)}`)
        .then(raw => {
          const data = (raw.script || raw) as ScriptLoadData
          if (data.code) {
            setCode(data.code)
            setLoadedScriptName(lastScript)
          }
        })
        .catch(() => {
          localStorage.removeItem(LAST_SCRIPT_KEY)
        })
    }
  }, [])  // eslint-disable-line react-hooks/exhaustive-deps

  // Fetch script list when menu opens
  const fetchScripts = useCallback(async () => {
    try {
      const raw = await apiGet<ScriptEntry[] | { scripts?: ScriptEntry[] }>('/api/v1/scripts/list')
      const list: ScriptEntry[] = Array.isArray(raw)
        ? raw
        : (raw as { scripts?: ScriptEntry[] }).scripts || []
      setScriptList(list)
    } catch {
      setScriptList([])
    }
  }, [])

  const handleOpenScriptMenu = useCallback(() => {
    fetchScripts()
    setShowScriptMenu(prev => !prev)
  }, [fetchScripts])

  // Close menu on outside click
  useEffect(() => {
    if (!showScriptMenu) return
    const handler = (e: MouseEvent) => {
      if (scriptMenuRef.current && !scriptMenuRef.current.contains(e.target as Node)) {
        setShowScriptMenu(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [showScriptMenu])

  const handleLoadScript = useCallback(async (name: string) => {
    setShowScriptMenu(false)
    try {
      const raw = await apiGet<Record<string, unknown>>(`/api/v1/scripts/load/${encodeURIComponent(name)}`)
      const data = (raw.script || raw) as ScriptLoadData
      setCode(data.code || '')
      setLoadedScriptName(name)
      localStorage.setItem(LAST_SCRIPT_KEY, name)
      setOutput('')
      setError('')
      setExecutionTime(null)
    } catch (err) {
      setError(`Failed to load script: ${err instanceof Error ? err.message : err}`)
    }
  }, [])

  // ---- Execute ----
  const handleExecute = useCallback(async () => {
    if (isExecuting) return
    setIsExecuting(true)
    setOutput('')
    setError('')
    setExecutionTime(null)

    try {
      const body: Record<string, unknown> = { code, variables: {} }
      if (selectedVersion) body.version = selectedVersion

      const data = await apiPost<{
        success: boolean
        result?: unknown
        output?: unknown
        error?: string
        traceback?: string
        executionTimeMs?: number
        executionTime?: number
      }>('/api/v1/exec', body, 60_000)

      if (data.success) {
        const resultText = data.result !== undefined
          ? String(data.result)
          : data.output !== undefined
            ? String(data.output)
            : ''
        setOutput(resultText)
        const timing = data.executionTimeMs ?? data.executionTime ?? null
        setExecutionTime(typeof timing === 'number' ? timing : null)
      } else {
        let errMsg = data.error ?? 'Execution failed'
        if (data.traceback) errMsg = errMsg + '\n\n' + data.traceback
        setError(errMsg)
        const timing = data.executionTimeMs ?? data.executionTime ?? null
        setExecutionTime(typeof timing === 'number' ? timing : null)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Network error')
    } finally {
      setIsExecuting(false)
    }
  }, [code, isExecuting, selectedVersion])

  // ---- Save (auto-save if script loaded, otherwise falls through to Save As) ----
  const handleSave = useCallback(async () => {
    if (!loadedScriptName) {
      // No script loaded — behave like Save As
      handleSaveAs()
      return
    }

    try {
      const data = await apiPost<{ success?: boolean; error?: string; message?: string }>(
        '/api/v1/scripts/save',
        { name: loadedScriptName, code },
        10_000,
      )
      if (data.success !== false) {
        setOutput(prev => (prev ? prev + '\n' : '') + `[Saved "${loadedScriptName}"]`)
      } else {
        setError(`Save failed: ${data.error ?? data.message ?? 'Unknown error'}`)
      }
    } catch (err) {
      setError(`Save failed: ${err instanceof Error ? err.message : 'Network error'}`)
    }
  }, [code, loadedScriptName]) // eslint-disable-line react-hooks/exhaustive-deps

  // ---- Save As (always prompts for new name) ----
  const handleSaveAs = useCallback(async () => {
    const defaultName = loadedScriptName || 'My Script'
    const name = window.prompt('Script name:', defaultName)
    if (!name || !name.trim()) return

    const description = window.prompt('Description (optional):', '') ?? ''

    try {
      const data = await apiPost<{ success?: boolean; error?: string; message?: string }>(
        '/api/v1/scripts/save',
        { name: name.trim(), code, description },
        10_000,
      )
      if (data.success !== false) {
        setLoadedScriptName(name.trim())
        localStorage.setItem(LAST_SCRIPT_KEY, name.trim())
        setOutput(prev => (prev ? prev + '\n' : '') + `[Saved as "${name.trim()}"]`)
      } else {
        setError(`Save failed: ${data.error ?? data.message ?? 'Unknown error'}`)
      }
    } catch (err) {
      setError(`Save failed: ${err instanceof Error ? err.message : 'Network error'}`)
    }
  }, [code, loadedScriptName])

  // ---- Clear ----
  const handleClear = useCallback(() => {
    setOutput('')
    setError('')
    setExecutionTime(null)
  }, [])

  const handleClearAll = useCallback(() => {
    setCode(DEFAULT_CODE)
    setOutput('')
    setError('')
    setExecutionTime(null)
    setLoadedScriptName(null)
    localStorage.removeItem(LAST_SCRIPT_KEY)
  }, [])

  // ---- Split toggle ----
  const handleToggleSplit = useCallback(() => {
    setSplitDirection(prev => {
      const next = prev === 'vertical' ? 'horizontal' : 'vertical'
      localStorage.setItem(SPLIT_DIR_KEY, next)
      setOutputSize(DEFAULT_OUTPUT_SIZE)
      return next
    })
  }, [])

  // ---- Resize handle drag (works for both vertical and horizontal) ----
  const handleResizeMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    isDragging.current = true
    dragStart.current = splitDirection === 'vertical' ? e.clientY : e.clientX
    dragStartSize.current = outputSize
    document.body.style.cursor = splitDirection === 'vertical' ? 'row-resize' : 'col-resize'
    document.body.style.userSelect = 'none'
  }, [outputSize, splitDirection])

  useEffect(() => {
    const onMouseMove = (e: MouseEvent) => {
      if (!isDragging.current || !containerRef.current) return
      const rect = containerRef.current.getBoundingClientRect()
      if (splitDirection === 'vertical') {
        const delta = dragStart.current - e.clientY
        const newSize = Math.max(80, Math.min(rect.height - 150, dragStartSize.current + delta))
        setOutputSize(newSize)
      } else {
        const delta = dragStart.current - e.clientX
        const newSize = Math.max(200, Math.min(rect.width - 300, dragStartSize.current + delta))
        setOutputSize(newSize)
      }
    }

    const onMouseUp = () => {
      if (!isDragging.current) return
      isDragging.current = false
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }

    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
    return () => {
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
    }
  }, [splitDirection])

  // Group scripts by folder for the dropdown
  const rootScripts = scriptList.filter(s => !s.folderPath)
  const folderMap: Record<string, ScriptEntry[]> = {}
  for (const s of scriptList) {
    if (s.folderPath) {
      if (!folderMap[s.folderPath]) folderMap[s.folderPath] = []
      folderMap[s.folderPath].push(s)
    }
  }

  return (
    <div className="ide-view" ref={containerRef}>
      {/* Toolbar */}
      <ExecutionToolbar
        onExecute={handleExecute}
        onClear={handleClearAll}
        onSave={handleSave}
        onSaveAs={handleSaveAs}
        isExecuting={isExecuting}
        splitDirection={splitDirection}
        onToggleSplit={handleToggleSplit}
      >
        <VersionSelector
          gatewayUrl={gatewayUrl}
          selectedVersion={selectedVersion}
          onVersionChange={setSelectedVersion}
        />
        <div className="ide-script-loader" ref={scriptMenuRef}>
          <button
            className="exec-toolbar-btn exec-toolbar-btn--secondary"
            onClick={handleOpenScriptMenu}
            title="Load a saved script"
          >
            Load Script
          </button>
          {showScriptMenu && (
            <div className="ide-script-menu">
              {scriptList.length === 0 ? (
                <div className="ide-script-menu-empty">No saved scripts</div>
              ) : (
                <>
                  {Object.keys(folderMap).sort().map(folder => (
                    <div key={folder}>
                      <div className="ide-script-menu-folder">{folder}</div>
                      {folderMap[folder].map(s => (
                        <button
                          key={s.name}
                          className="ide-script-menu-item ide-script-menu-item--indented"
                          onClick={() => handleLoadScript(s.name)}
                          title={s.description || s.name}
                        >
                          {s.name}
                        </button>
                      ))}
                    </div>
                  ))}
                  {rootScripts.map(s => (
                    <button
                      key={s.name}
                      className="ide-script-menu-item"
                      onClick={() => handleLoadScript(s.name)}
                      title={s.description || s.name}
                    >
                      {s.name}
                    </button>
                  ))}
                </>
              )}
            </div>
          )}
        </div>
      </ExecutionToolbar>

      {/* Loaded script indicator */}
      {loadedScriptName && (
        <div className="ide-loaded-script-bar">
          Editing: <strong>{loadedScriptName}</strong>
        </div>
      )}

      {/* Content area (switches between column and row layout) */}
      <div className={`ide-content-area ide-content-area--${splitDirection}`}>
        {/* Editor */}
        <div className="ide-editor-area">
          <CodeEditor
            value={code}
            onChange={setCode}
            onExecute={handleExecute}
          />
        </div>

        {/* Resize handle */}
        <div
          className={`ide-resize-handle ide-resize-handle--${splitDirection}`}
          onMouseDown={handleResizeMouseDown}
          title="Drag to resize output panel"
          role="separator"
          aria-label="Resize output panel"
        />

        {/* Output panel */}
        <div
          className="ide-output-area"
          style={splitDirection === 'vertical'
            ? { height: outputSize }
            : { width: outputSize }
          }
        >
          <OutputPanel
            output={output}
            error={error}
            executionTime={executionTime}
            isExecuting={isExecuting}
            onClear={handleClear}
          />
        </div>
      </div>
    </div>
  )
}

export default IDEView
