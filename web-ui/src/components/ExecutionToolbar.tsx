import { ReactNode } from 'react'
import { Play, Trash2, Save, Copy, Loader, Columns, Rows } from 'lucide-react'

interface Props {
  onExecute: () => void
  onClear: () => void
  onSave: () => void
  onSaveAs: () => void
  isExecuting: boolean
  splitDirection?: 'vertical' | 'horizontal'
  onToggleSplit?: () => void
  children?: ReactNode
}

function ExecutionToolbar({ onExecute, onClear, onSave, onSaveAs, isExecuting, splitDirection, onToggleSplit, children }: Props) {
  return (
    <div className="execution-toolbar">
      {/* Run button */}
      <button
        className={`exec-toolbar-btn exec-toolbar-btn--run ${isExecuting ? 'exec-toolbar-btn--running' : ''}`}
        onClick={onExecute}
        disabled={isExecuting}
        title="Run script (Ctrl+Enter)"
        aria-label="Run script"
      >
        {isExecuting ? (
          <>
            <Loader size={14} className="spin-sm" />
            Running...
          </>
        ) : (
          <>
            <Play size={14} />
            Run
          </>
        )}
      </button>

      <div className="exec-toolbar-separator" />

      {/* Version selector slot (children) */}
      {children && <div className="exec-toolbar-slot">{children}</div>}

      <div className="exec-toolbar-spacer" />

      {/* Split toggle */}
      {onToggleSplit && (
        <button
          className="exec-toolbar-btn exec-toolbar-btn--ghost"
          onClick={onToggleSplit}
          title={splitDirection === 'horizontal' ? 'Switch to vertical split' : 'Switch to horizontal split'}
          aria-label="Toggle split orientation"
        >
          {splitDirection === 'horizontal' ? <Rows size={13} /> : <Columns size={13} />}
          Split
        </button>
      )}

      {/* Save button */}
      <button
        className="exec-toolbar-btn exec-toolbar-btn--secondary"
        onClick={onSave}
        disabled={isExecuting}
        title="Save script (Ctrl+S)"
        aria-label="Save script"
      >
        <Save size={13} />
        Save
      </button>

      {/* Save As button */}
      <button
        className="exec-toolbar-btn exec-toolbar-btn--secondary"
        onClick={onSaveAs}
        disabled={isExecuting}
        title="Save as new script"
        aria-label="Save as new script"
      >
        <Copy size={13} />
        Save As
      </button>

      {/* Clear button */}
      <button
        className="exec-toolbar-btn exec-toolbar-btn--ghost"
        onClick={onClear}
        title="Clear editor and output"
        aria-label="Clear editor"
      >
        <Trash2 size={13} />
        Clear
      </button>
    </div>
  )
}

export default ExecutionToolbar
