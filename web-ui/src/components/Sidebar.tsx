import { useState, useEffect, useCallback } from 'react'
import {
  LayoutDashboard, Code2, FileCode,
  Layers, Package, BarChart3, ChevronLeft, ChevronRight,
  ExternalLink
} from 'lucide-react'
import './Sidebar.css'

interface SidebarProps {
  activeView: string
  onNavigate: (view: string) => void
  gatewayUrl?: string
}

interface NavItem {
  id: string
  label: string
  icon: React.ComponentType<{ size?: number | string }>
  enabled: boolean
}

const navItems: NavItem[] = [
  { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard, enabled: true },
  { id: 'ide', label: 'IDE', icon: Code2, enabled: true },
  { id: 'scripts', label: 'Scripts', icon: FileCode, enabled: true },
  { id: 'versions', label: 'Python Versions', icon: Layers, enabled: true },
  { id: 'packages', label: 'Packages', icon: Package, enabled: true },
  { id: 'diagnostics', label: 'Diagnostics', icon: BarChart3, enabled: true },
]

const STORAGE_KEY = 'python3-sidebar-collapsed'

function Sidebar({ activeView, onNavigate, gatewayUrl }: SidebarProps) {
  const [isCollapsed, setIsCollapsed] = useState<boolean>(() => {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  })
  // Fallback module version - ALWAYS UPDATE THIS WITH NEW RELEASES
  const FALLBACK_MODULE_VERSION = '3.8.1'

  const [moduleVersion, setModuleVersion] = useState<string>(FALLBACK_MODULE_VERSION)

  useEffect(() => {
    if (!gatewayUrl) return
    fetch(`${gatewayUrl}/api/v1/version`)
      .then(res => res.ok ? res.json() : null)
      .then(raw => {
        const data = raw?.data || raw
        // Use moduleVersion field if present; the `version` field is the Python version, not the module version
        if (data?.moduleVersion) {
          setModuleVersion(data.moduleVersion)
        }
        // If no moduleVersion field, keep the hardcoded fallback
      })
      .catch(() => {})
  }, [gatewayUrl])

  const toggleCollapse = useCallback(() => {
    setIsCollapsed(prev => {
      const next = !prev
      localStorage.setItem(STORAGE_KEY, next.toString())
      return next
    })
  }, [])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'b') {
        e.preventDefault()
        toggleCollapse()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [toggleCollapse])

  const handleItemClick = useCallback((item: NavItem) => {
    if (!item.enabled) return
    onNavigate(item.id)
  }, [onNavigate])

  return (
    <nav className={`nav-sidebar ${isCollapsed ? 'collapsed' : 'expanded'}`}>
      <div className="nav-sidebar-items">
        {navItems.map(item => {
          const Icon = item.icon
          const isActive = activeView === item.id
          const classNames = [
            'nav-sidebar-item',
            isActive ? 'active' : '',
            !item.enabled ? 'disabled' : '',
          ].filter(Boolean).join(' ')

          return (
            <button
              key={item.id}
              className={classNames}
              onClick={() => handleItemClick(item)}
              title={!isCollapsed && item.enabled ? item.label : undefined}
              aria-label={item.label}
              aria-current={isActive ? 'page' : undefined}
              disabled={!item.enabled}
            >
              <span className="nav-sidebar-item-icon">
                <Icon size={18} />
              </span>
              <span className="nav-sidebar-item-label">{item.label}</span>
              {(isCollapsed || !item.enabled) && (
                <span className="nav-sidebar-tooltip">
                  {item.enabled ? item.label : `${item.label} - Coming Soon`}
                </span>
              )}
            </button>
          )
        })}
      </div>

      {moduleVersion && (
        <div className="nav-sidebar-version" title={`Module v${moduleVersion}`}>
          <span className="nav-sidebar-version-text">v{moduleVersion}</span>
        </div>
      )}

      <button
        className="nav-sidebar-dedicated-btn"
        onClick={() => window.open('/res/python3integration/standalone.html', '_blank')}
        title="Open dedicated full-page Python 3 IDE"
        aria-label="Open dedicated full-page Python 3 IDE"
      >
        <ExternalLink size={14} />
        <span className="nav-sidebar-dedicated-label">Dedicated Page</span>
      </button>

      <button
        className="nav-sidebar-toggle"
        onClick={toggleCollapse}
        title={isCollapsed ? 'Expand sidebar (Ctrl+B)' : 'Collapse sidebar (Ctrl+B)'}
        aria-label={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      >
        {isCollapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
        <span className="nav-sidebar-toggle-label">Collapse</span>
      </button>
    </nav>
  )
}

export default Sidebar
