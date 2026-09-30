import { ReactNode } from 'react'

interface StatCardProps {
  title: string
  icon?: ReactNode
  children: ReactNode
  status?: 'healthy' | 'degraded' | 'down' | 'neutral'
  loading?: boolean
}

function StatCard({ title, icon, children, status = 'neutral', loading = false }: StatCardProps) {
  const statusClass = status !== 'neutral' ? `stat-card--${status}` : ''

  return (
    <div className={`stat-card ${statusClass}`}>
      <div className="stat-card__header">
        {icon && <span className="stat-card__icon">{icon}</span>}
        <span className="stat-card__title">{title}</span>
      </div>
      <div className="stat-card__body">
        {loading ? (
          <div className="stat-card__skeleton">
            <div className="skeleton-line skeleton-line--wide" />
            <div className="skeleton-line skeleton-line--medium" />
          </div>
        ) : (
          children
        )}
      </div>
    </div>
  )
}

export default StatCard
