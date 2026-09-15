interface Metrics {
  totalExecutions: number
  successfulExecutions: number
  failedExecutions: number
  averageLatencyMs: number
  maxLatencyMs: number
  errorRate: number
}

interface MetricsPanelProps {
  metrics: Metrics | null
}

function latencyClass(ms: number): string {
  if (ms < 100) return 'diag-metric-value--good'
  if (ms < 500) return 'diag-metric-value--warn'
  return 'diag-metric-value--danger'
}

function MetricsPanel({ metrics }: MetricsPanelProps) {
  if (!metrics) {
    return (
      <div className="diag-panel">
        <h3 className="diag-panel__title">Execution Metrics</h3>
        <p className="diag-panel__empty">No metrics data available.</p>
      </div>
    )
  }

  const {
    totalExecutions,
    successfulExecutions,
    failedExecutions,
    averageLatencyMs,
    maxLatencyMs,
    errorRate,
  } = metrics

  const errorRateClass = errorRate > 5 ? 'diag-metric-value--danger' : 'diag-metric-value--good'
  const avgLatClass = latencyClass(averageLatencyMs)
  const maxLatClass = latencyClass(maxLatencyMs)

  return (
    <div className="diag-panel">
      <h3 className="diag-panel__title">Execution Metrics</h3>

      <table className="diag-metrics-table">
        <tbody>
          <tr className="diag-metrics-row">
            <td className="diag-metrics-label">Total Executions</td>
            <td className="diag-metrics-value">{totalExecutions.toLocaleString()}</td>
          </tr>
          <tr className="diag-metrics-row">
            <td className="diag-metrics-label">Successful</td>
            <td className="diag-metrics-value diag-metric-value--good">
              {successfulExecutions.toLocaleString()}
            </td>
          </tr>
          <tr className="diag-metrics-row">
            <td className="diag-metrics-label">Failed</td>
            <td className={`diag-metrics-value ${failedExecutions > 0 ? 'diag-metric-value--danger' : ''}`}>
              {failedExecutions.toLocaleString()}
            </td>
          </tr>
          <tr className="diag-metrics-divider">
            <td colSpan={2} />
          </tr>
          <tr className="diag-metrics-row">
            <td className="diag-metrics-label">Avg Latency</td>
            <td className={`diag-metrics-value ${avgLatClass}`}>
              {averageLatencyMs.toFixed(1)} ms
            </td>
          </tr>
          <tr className="diag-metrics-row">
            <td className="diag-metrics-label">Max Latency</td>
            <td className={`diag-metrics-value ${maxLatClass}`}>
              {maxLatencyMs.toFixed(1)} ms
            </td>
          </tr>
          <tr className="diag-metrics-divider">
            <td colSpan={2} />
          </tr>
          <tr className="diag-metrics-row">
            <td className="diag-metrics-label">Error Rate</td>
            <td className={`diag-metrics-value ${errorRateClass}`}>
              {errorRate.toFixed(2)}%
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  )
}

export default MetricsPanel
