import { useState, useEffect, useRef, useCallback } from 'react'
import { Code2 } from 'lucide-react'
import ModuleHeader from './components/ModuleHeader'
import Sidebar from './components/Sidebar'
import ErrorBoundary from './components/ErrorBoundary'
import GlobalStatusBar from './components/GlobalStatusBar'
import DashboardView from './components/DashboardView'
import IDEView from './components/IDEView'
import ScriptsView from './components/ScriptsView'
import VersionsView from './components/VersionsView'
import PackagesView from './components/PackagesView'
import DiagnosticsView from './components/DiagnosticsView'
import Toast from './components/Toast'
import { initSession } from './utils/api'
import './styles.css'
import './App.css'

const SINGLETON_KEY = '__python3_ide_singleton_v1__'
const INSTANCE_ID = `inst-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`

let IS_PRIMARY_MODULE = false
try {
  Object.defineProperty(window, SINGLETON_KEY, {
    value: INSTANCE_ID,
    writable: false,
    configurable: false,
    enumerable: false
  })
  IS_PRIMARY_MODULE = true
} catch {
  IS_PRIMARY_MODULE = false
}

const GATEWAY_URL = window.location.origin + '/data/python3integration'

function App() {
  if (!IS_PRIMARY_MODULE) {
    return null
  }
  return <AppContent />
}

interface ToastEntry {
  id: number
  message: string
  type: 'success' | 'error' | 'info'
}

let toastIdCounter = 0

function AppContent() {
  const [activeView, setActiveView] = useState<string>('dashboard')
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'disconnected' | 'auth_required'>('connecting')
  const [toasts, setToasts] = useState<ToastEntry[]>([])
  const healthCheckIntervalRef = useRef<number | null>(null)
  const healthCheckAttempts = useRef<number>(0)
  const currentHealthInterval = useRef<number>(5000)

  const showToast = useCallback((message: string, type: 'success' | 'error' | 'info') => {
    const id = ++toastIdCounter
    setToasts(prev => [...prev, { id, message, type }])
  }, [])

  const removeToast = useCallback((id: number) => {
    setToasts(prev => prev.filter(t => t.id !== id))
  }, [])

  const checkHealth = useCallback(async () => {
    try {
      const res = await fetch(`${GATEWAY_URL}/api/v1/health`, {
        signal: AbortSignal.timeout(5000),
        credentials: 'same-origin',
      })
      // Detect HTML response = login page redirect = not authenticated
      const contentType = res.headers.get('content-type') || ''
      if (contentType.includes('text/html') || res.status === 401 || res.status === 403) {
        setConnectionStatus('auth_required')
        currentHealthInterval.current = 10000
      } else if (res.ok) {
        await res.json()
        setConnectionStatus('connected')
        healthCheckAttempts.current = 0
        currentHealthInterval.current = 15000
      } else {
        throw new Error(`HTTP ${res.status}`)
      }
    } catch {
      healthCheckAttempts.current++
      setConnectionStatus(healthCheckAttempts.current > 2 ? 'disconnected' : 'connecting')
      currentHealthInterval.current = Math.min(5000 * Math.pow(2, healthCheckAttempts.current), 30000)
    }

    if (healthCheckIntervalRef.current) {
      window.clearTimeout(healthCheckIntervalRef.current)
    }
    healthCheckIntervalRef.current = window.setTimeout(checkHealth, currentHealthInterval.current)
  }, [])

  useEffect(() => {
    // Initialize CSRF session token before any POST requests are made
    const init = async () => {
      await initSession()
      checkHealth()
    }
    init()
    return () => {
      if (healthCheckIntervalRef.current) {
        window.clearTimeout(healthCheckIntervalRef.current)
      }
    }
  }, [checkHealth])

  const renderView = () => {
    switch (activeView) {
      case 'dashboard':
        return <DashboardView gatewayUrl={GATEWAY_URL} onNavigate={setActiveView} />
      case 'ide':
        return <IDEView gatewayUrl={GATEWAY_URL} />
      case 'scripts':
        return <ScriptsView gatewayUrl={GATEWAY_URL} showToast={showToast} />
      case 'versions':
        return <VersionsView gatewayUrl={GATEWAY_URL} showToast={showToast} />
      case 'packages':
        return <PackagesView gatewayUrl={GATEWAY_URL} showToast={showToast} />
      case 'diagnostics':
        return <DiagnosticsView gatewayUrl={GATEWAY_URL} />
      default:
        return <DashboardView gatewayUrl={GATEWAY_URL} onNavigate={setActiveView} />
    }
  }

  if (connectionStatus === 'auth_required') {
    return (
      <div className="app-wrapper">
        <div className="app-container">
          <div className="auth-required-overlay">
            <div className="auth-required-card">
              <div className="auth-module-identity">
                <Code2 size={32} />
                <span>Python 3 Integration</span>
              </div>
              <div className="auth-divider" />
              <div className="auth-icon">&#128274;</div>
              <h2>Authentication Required</h2>
              <p>You must be logged in to the Ignition Gateway to use this module.</p>
              <a href="/web/login" target="_top" className="auth-login-btn">
                Log In to Gateway
              </a>
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="app-wrapper">
      <a href="#main-content" className="skip-link">Skip to main content</a>
      <div className="app-container">
        <ModuleHeader />
        {connectionStatus === 'disconnected' && (
          <div className="connection-banner" role="status" aria-live="polite">
            Unable to connect to Python 3 Integration gateway. Retrying...
          </div>
        )}
        <div className="app-outer-layout">
          <Sidebar activeView={activeView} onNavigate={setActiveView} gatewayUrl={GATEWAY_URL} />
          <div className="app-content-area">
            <main id="main-content" className="main-content" tabIndex={-1}>
              <ErrorBoundary>
                {renderView()}
              </ErrorBoundary>
            </main>
            <GlobalStatusBar gatewayUrl={GATEWAY_URL} />
          </div>
        </div>
      </div>
      <div
        className="toast-container"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        {toasts.map(t => (
          <Toast key={t.id} message={t.message} type={t.type} onClose={() => removeToast(t.id)} />
        ))}
      </div>
    </div>
  )
}

export default App
