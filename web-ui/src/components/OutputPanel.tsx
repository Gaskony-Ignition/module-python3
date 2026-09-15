import { useState } from 'react'
import { Clock, Trash2, Loader } from 'lucide-react'

interface Props {
  output: string
  error: string
  executionTime: number | null
  isExecuting: boolean
  onClear: () => void
}

type Tab = 'output' | 'errors'

function OutputPanel({ output, error, executionTime, isExecuting, onClear }: Props) {
  const [activeTab, setActiveTab] = useState<Tab>('output')

  const errorCount = error
    ? error.split('\n').filter(l => l.trim().length > 0).length
    : 0

  return (
    <div className="output-panel">
      {/* Tab bar */}
      <div className="output-panel__tabs">
        <button
          className={`output-panel__tab ${activeTab === 'output' ? 'output-panel__tab--active' : ''}`}
          onClick={() => setActiveTab('output')}
        >
          Output
          {isExecuting && activeTab !== 'output' && (
            <span className="output-panel__tab-badge output-panel__tab-badge--running">
              <Loader size={10} className="spin-sm" />
            </span>
          )}
        </button>
        <button
          className={`output-panel__tab ${activeTab === 'errors' ? 'output-panel__tab--active' : ''}`}
          onClick={() => setActiveTab('errors')}
        >
          Errors
          {errorCount > 0 && (
            <span className="output-panel__tab-badge output-panel__tab-badge--error">
              {errorCount}
            </span>
          )}
        </button>

        <div className="output-panel__spacer" />

        {/* Execution time */}
        {executionTime !== null && !isExecuting && (
          <span className="output-panel__timing">
            <Clock size={11} />
            {executionTime < 1000
              ? `${executionTime} ms`
              : `${(executionTime / 1000).toFixed(2)} s`}
          </span>
        )}

        {isExecuting && (
          <span className="output-panel__timing output-panel__timing--running">
            <Loader size={11} className="spin-sm" />
            Running...
          </span>
        )}

        {/* Clear button */}
        <button
          className="output-panel__clear-btn"
          onClick={onClear}
          title="Clear output"
          aria-label="Clear output"
        >
          <Trash2 size={12} />
          Clear
        </button>
      </div>

      {/* Content */}
      <div className="output-panel__content">
        {activeTab === 'output' && (
          <pre className="output-panel__pre output-panel__pre--output">
            {isExecuting && !output ? (
              <span className="output-panel__placeholder">Executing...</span>
            ) : output ? (
              output
            ) : (
              <span className="output-panel__placeholder">
                Run a script to see output here. (Ctrl+Enter)
              </span>
            )}
          </pre>
        )}

        {activeTab === 'errors' && (
          <pre className="output-panel__pre output-panel__pre--error">
            {error ? (
              error
            ) : (
              <span className="output-panel__placeholder output-panel__placeholder--ok">
                No errors.
              </span>
            )}
          </pre>
        )}
      </div>
    </div>
  )
}

export default OutputPanel
