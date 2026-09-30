import { useEffect } from 'react'
import { X } from 'lucide-react'
import './Toast.css'

interface ToastProps {
  message: string
  type: 'success' | 'error' | 'info'
  onClose: () => void
}

function Toast({ message, type, onClose }: ToastProps) {
  useEffect(() => {
    const duration = type === 'error' ? 6000 : 4000
    const timer = window.setTimeout(onClose, duration)
    return () => window.clearTimeout(timer)
  }, [type, onClose])

  return (
    <div className={`toast toast-${type}`}>
      <span>{message}</span>
      <button className="toast-close" onClick={onClose} aria-label="Close">
        <X size={14} />
      </button>
    </div>
  )
}

export default Toast
