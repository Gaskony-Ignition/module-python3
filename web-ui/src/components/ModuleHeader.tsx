import { Code2 } from 'lucide-react'
import './ModuleHeader.css'

function ModuleHeader() {
  return (
    <div className="module-header">
      <Code2 size={16} className="module-header__icon" />
      <span className="module-header__title">Python 3 Integration</span>
    </div>
  )
}

export default ModuleHeader
