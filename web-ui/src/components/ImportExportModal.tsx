import { useState, useRef } from 'react'
import { Upload } from 'lucide-react'
import Modal from './Modal'
import { apiPost } from '../utils/api'

interface ImportExportModalProps {
  isOpen: boolean
  onClose: () => void
  onImported: () => void
}

function ImportExportModal({ isOpen, onClose, onImported }: ImportExportModalProps) {
  const [scriptName, setScriptName] = useState<string>('')
  const [description, setDescription] = useState<string>('')
  const [fileContent, setFileContent] = useState<string>('')
  const [fileName, setFileName] = useState<string>('')
  const [status, setStatus] = useState<'idle' | 'saving' | 'success' | 'error'>('idle')
  const [errorMsg, setErrorMsg] = useState<string>('')
  const fileInputRef = useRef<HTMLInputElement>(null)

  if (!isOpen) return null

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const baseName = file.name.replace(/\.py$/i, '')
    setFileName(file.name)
    if (!scriptName) {
      setScriptName(baseName)
    }

    const reader = new FileReader()
    reader.onload = (ev) => {
      setFileContent(ev.target?.result as string || '')
    }
    reader.onerror = () => {
      setErrorMsg('Failed to read file.')
      setStatus('error')
    }
    reader.readAsText(file)
  }

  const handleImport = async () => {
    if (!scriptName.trim()) {
      setErrorMsg('Script name is required.')
      setStatus('error')
      return
    }
    if (!fileContent) {
      setErrorMsg('Please select a .py file.')
      setStatus('error')
      return
    }

    setStatus('saving')
    setErrorMsg('')

    try {
      await apiPost('/api/v1/scripts/save', {
        name: scriptName.trim(),
        code: fileContent,
        description: description.trim(),
      })

      setStatus('success')
      onImported()

      // Auto-close after 1.5 seconds on success
      setTimeout(() => {
        handleClose()
      }, 1500)
    } catch (err) {
      setErrorMsg(`Import failed: ${err}`)
      setStatus('error')
    }
  }

  const handleClose = () => {
    setScriptName('')
    setDescription('')
    setFileContent('')
    setFileName('')
    setStatus('idle')
    setErrorMsg('')
    if (fileInputRef.current) fileInputRef.current.value = ''
    onClose()
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={
        <>
          <Upload size={16} />
          Import Python Script
        </>
      }
      footer={
        <>
          <button className="modal-btn" onClick={handleClose}>
            Cancel
          </button>
          <button
            className="modal-btn primary"
            onClick={handleImport}
            disabled={status === 'saving' || !fileContent}
          >
            {status === 'saving' ? 'Importing...' : 'Import'}
          </button>
        </>
      }
    >
      {/* File picker */}
      <div className="modal-field">
        <label className="modal-label" htmlFor="import-file-input">Select .py file</label>
        <div className="modal-file-row">
          <input
            ref={fileInputRef}
            type="file"
            accept=".py"
            onChange={handleFileChange}
            className="modal-file-input"
            id="import-file-input"
          />
          <label htmlFor="import-file-input" className="modal-file-btn">
            <Upload size={13} />
            Choose file
          </label>
          {fileName && (
            <span className="modal-file-name">{fileName}</span>
          )}
        </div>
      </div>

      {/* Script name */}
      <div className="modal-field">
        <label className="modal-label" htmlFor="import-script-name">Script name *</label>
        <input
          id="import-script-name"
          type="text"
          className="modal-input"
          value={scriptName}
          onChange={e => setScriptName(e.target.value)}
          placeholder="e.g. my_script"
        />
      </div>

      {/* Description */}
      <div className="modal-field">
        <label className="modal-label" htmlFor="import-description">Description (optional)</label>
        <input
          id="import-description"
          type="text"
          className="modal-input"
          value={description}
          onChange={e => setDescription(e.target.value)}
          placeholder="Brief description..."
        />
      </div>

      {/* Status messages */}
      {status === 'error' && (
        <div className="modal-status error" role="alert">{errorMsg}</div>
      )}
      {status === 'success' && (
        <div className="modal-status success" role="status">Script imported successfully!</div>
      )}
    </Modal>
  )
}

export default ImportExportModal
