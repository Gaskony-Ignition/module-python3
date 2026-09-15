# Python 3 Integration - Monitoring Guide

**Status:** Production-Ready

## Overview

The Python 3 Integration module exposes comprehensive metrics via a Prometheus-compatible endpoint for monitoring process pool health, execution statistics, and performance metrics.

## Prometheus Metrics Endpoint

### Endpoint URL

```
GET http://<gateway-host>:<gateway-port>/data/python3integration/api/v1/monitoring/prometheus
```

**Default Ignition Gateway:** `http://localhost:8088/data/python3integration/api/v1/monitoring/prometheus`

### Authentication

The `/monitoring/prometheus` endpoint requires **read permission** (same as other monitoring endpoints).

- **API Key Authentication:** Pass API key in `Authorization` header
- **Session Authentication:** Login via Ignition Gateway

**Example with API Key:**
```bash
curl -H "Authorization: Bearer <your-api-key>" \
  http://localhost:8088/data/python3integration/api/v1/monitoring/prometheus
```

### Response Format

The endpoint returns metrics in **Prometheus text exposition format**:

```
# HELP python3_pool_size_total Total number of executors in the pool
# TYPE python3_pool_size_total gauge
python3_pool_size_total 5

# HELP python3_pool_available Number of available executors
# TYPE python3_pool_available gauge
python3_pool_available 3

# HELP python3_executions_total Total number of Python executions
# TYPE python3_executions_total counter
python3_executions_total 1234
```

### Available Metrics

#### Pool Metrics (Gauges)

| Metric Name | Type | Description |
|------------|------|-------------|
| `python3_pool_size_total` | gauge | Total number of executors in the pool |
| `python3_pool_available` | gauge | Number of available (idle) executors |
| `python3_pool_in_use` | gauge | Number of executors currently executing code |
| `python3_pool_utilization_percent` | gauge | Pool utilization percentage (0-100%) |
| `python3_pool_healthy_executors` | gauge | Number of healthy executors |
| `python3_pool_unhealthy_executors` | gauge | Number of unhealthy executors |

#### Execution Metrics (Counters)

| Metric Name | Type | Description |
|------------|------|-------------|
| `python3_executions_total` | counter | Total number of Python executions |
| `python3_executions_success_total` | counter | Total number of successful executions |
| `python3_executions_failed_total` | counter | Total number of failed executions |

#### Performance Metrics (Gauges)

| Metric Name | Type | Description |
|------------|------|-------------|
| `python3_error_rate_percent` | gauge | Percentage of failed executions (0-100%) |
| `python3_response_time_ms_avg` | gauge | Average execution response time (milliseconds) |
| `python3_response_time_ms_p50` | gauge | 50th percentile response time (median) |
| `python3_response_time_ms_p95` | gauge | 95th percentile response time |
| `python3_response_time_ms_p99` | gauge | 99th percentile response time |

#### System Metrics (Counters)

| Metric Name | Type | Description |
|------------|------|-------------|
| `python3_uptime_seconds` | counter | Time since metrics collector started (seconds) |

## Prometheus Setup

### 1. Configure Prometheus Scrape Target

Add the Python 3 Integration endpoint to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'ignition-python3'
    scrape_interval: 15s
    scrape_timeout: 10s
    metrics_path: '/data/python3integration/api/v1/monitoring/prometheus'
    static_configs:
      - targets: ['localhost:8088']  # Your Ignition Gateway host:port
    # Optional: Authentication
    bearer_token: 'your-api-key-here'
    # Or use basic auth if configured
    # basic_auth:
    #   username: 'your-username'
    #   password: 'your-password'
```

### 2. Verify Metrics Collection

Check Prometheus targets page: `http://localhost:9090/targets`

Expected status: **UP** for `ignition-python3` target

### 3. Query Metrics

Open Prometheus query UI: `http://localhost:9090/graph`

**Example Queries:**

- Pool utilization: `python3_pool_utilization_percent`
- Execution rate: `rate(python3_executions_total[5m]) * 60`
- Error rate: `python3_error_rate_percent`
- Response time p95: `python3_response_time_ms_p95`

## Grafana Dashboard

### Import Pre-Built Dashboard

1. Open Grafana: `http://localhost:3000` (default)
2. Navigate to **Dashboards → Import**
3. Upload `grafana-dashboard-python3-pool.json` (this directory)
4. Select Prometheus data source
5. Click **Import**

### Dashboard Panels

The pre-built dashboard includes:

1. **Pool Size and Utilization** - Line chart showing total, available, and in-use executors
2. **Pool Utilization %** - Gauge showing current utilization percentage
3. **Executor Health** - Line chart showing healthy vs unhealthy executors
4. **Execution Rate** - Line chart showing executions per minute (total, success, failed)
5. **Error Rate %** - Gauge showing current error rate percentage
6. **Response Time Percentiles** - Line chart showing avg, p50, p95, p99 response times
7. **Execution Success vs Failure** - Pie chart showing execution breakdown
8. **Total Executions** - Stat panel showing total execution count
9. **Pool Uptime** - Stat panel showing metrics collector uptime

### Dashboard Variables

The dashboard uses the `${DS_PROMETHEUS}` variable for the Prometheus data source. When importing, Grafana will prompt you to select your Prometheus instance.

### Dashboard Settings

- **Refresh Rate:** 10 seconds (configurable in top-right)
- **Time Range:** Last 1 hour (adjustable)
- **Auto-Refresh:** Enabled by default

## Alerting

### Recommended Alerts

#### High Pool Utilization
```yaml
- alert: Python3PoolHighUtilization
  expr: python3_pool_utilization_percent > 80
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Python 3 pool utilization is high ({{ $value }}%)"
    description: "Pool has been above 80% utilization for 5 minutes. Consider increasing pool size."
```

#### High Error Rate
```yaml
- alert: Python3HighErrorRate
  expr: python3_error_rate_percent > 5
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Python 3 error rate is high ({{ $value }}%)"
    description: "Error rate has been above 5% for 5 minutes. Check logs for errors."
```

#### Unhealthy Executors
```yaml
- alert: Python3UnhealthyExecutors
  expr: python3_pool_unhealthy_executors > 0
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "Python 3 pool has {{ $value }} unhealthy executors"
    description: "Some executors are unhealthy. They will be automatically replaced."
```

#### Slow Response Time
```yaml
- alert: Python3SlowResponseTime
  expr: python3_response_time_ms_p95 > 5000
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Python 3 p95 response time is slow ({{ $value }}ms)"
    description: "95th percentile response time is above 5 seconds."
```

### Configuring Alerts in Prometheus

Add alerts to `prometheus.yml` or `alerts.rules.yml`:

```yaml
groups:
  - name: python3_pool_alerts
    interval: 30s
    rules:
      - alert: Python3PoolHighUtilization
        expr: python3_pool_utilization_percent > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Python 3 pool utilization is high ({{ $value }}%)"
```

### Configuring Alerts in Grafana

1. Open dashboard panel
2. Click panel title → **Edit**
3. Navigate to **Alert** tab
4. Click **Create Alert**
5. Set condition (e.g., `WHEN avg() OF query(A, 5m, now) IS ABOVE 80`)
6. Configure notification channel
7. Save dashboard

## Troubleshooting

### Endpoint Returns 503 "Process pool not available"

**Cause:** Process pool is not initialized or module is not loaded.

**Solution:**
- Check Ignition Gateway status: `http://localhost:8088/StatusPing`
- Verify module is installed: Gateway → Config → System → Modules
- Check Gateway logs: `<ignition-install>/logs/wrapper.log`

### No Data in Prometheus

**Cause:** Scrape target is down or authentication failed.

**Solution:**
- Verify endpoint is accessible: `curl http://localhost:8088/data/python3integration/api/v1/monitoring/prometheus`
- Check Prometheus targets page for errors
- Verify API key or authentication is correct
- Check Prometheus logs for scrape errors

### Metrics Are Stale

**Cause:** Prometheus scrape interval is too long or no executions are happening.

**Solution:**
- Reduce `scrape_interval` in Prometheus config (default: 15s)
- Verify Python executions are running (check `python3_executions_total`)
- Check if process pool is frozen or crashed

### Dashboard Shows "No Data"

**Cause:** Prometheus data source is not configured or queries are incorrect.

**Solution:**
- Verify Prometheus data source in Grafana: Settings → Data Sources
- Test Prometheus connection
- Check dashboard variables (`${DS_PROMETHEUS}`)
- Verify metrics exist in Prometheus: `http://localhost:9090/graph`

## Advanced Configuration

### Custom Scrape Intervals

For high-frequency monitoring (e.g., real-time dashboards):

```yaml
scrape_configs:
  - job_name: 'ignition-python3-realtime'
    scrape_interval: 5s  # Scrape every 5 seconds
    scrape_timeout: 3s
    metrics_path: '/data/python3integration/api/v1/monitoring/prometheus'
    static_configs:
      - targets: ['localhost:8088']
```

**Note:** More frequent scraping increases load on Gateway. Monitor impact.

### Multi-Gateway Setup

To monitor multiple Ignition Gateways:

```yaml
scrape_configs:
  - job_name: 'ignition-python3-multi'
    scrape_interval: 15s
    metrics_path: '/data/python3integration/api/v1/monitoring/prometheus'
    static_configs:
      - targets:
          - 'gateway1.example.com:8088'
          - 'gateway2.example.com:8088'
          - 'gateway3.example.com:8088'
        labels:
          environment: 'production'
      - targets:
          - 'gateway-dev.example.com:8088'
        labels:
          environment: 'development'
```

Then use `environment` label in Grafana queries:
```
python3_pool_utilization_percent{environment="production"}
```

## Security Considerations

1. **Endpoint Access:** The `/monitoring/prometheus` endpoint is protected by read permissions. Ensure only authorized users/services have API keys.

2. **HTTPS:** For production deployments, use HTTPS to encrypt metrics in transit:
   ```yaml
   scheme: https
   tls_config:
     insecure_skip_verify: false  # Verify SSL certificates
   ```

3. **API Key Rotation:** Rotate API keys regularly and revoke old keys.

4. **Network Access:** Restrict Prometheus scraper access to Ignition Gateway via firewall rules.

## Performance Impact

**Metrics Collection Overhead:**
- Negligible CPU impact (< 0.1%)
- Negligible memory impact (< 5MB for 1,000 samples)
- Scrape time: ~10-50ms per scrape

**Recommendations:**
- Use 15-30s scrape intervals for production
- Avoid scraping more frequently than 5s unless necessary
- Monitor Prometheus server resource usage

## References

- **Prometheus Exposition Formats:** https://prometheus.io/docs/instrumenting/exposition_formats/
- **Grafana Dashboard Best Practices:** https://grafana.com/docs/grafana/latest/dashboards/
- **Prometheus Alerting Rules:** https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/

## Support

For issues or questions:
- **GitHub Issues:** https://github.com/Gaskony-Ignition/ignition-module-python3/issues
- **Module Documentation:** [README.md](../../README.md)
