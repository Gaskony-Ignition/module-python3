# Python 3 Integration - Monitoring & Alerting Guide

**Audience:** DevOps, SRE, System Administrators

---

## Table of Contents

1. [Overview](#overview)
2. [Key Metrics](#key-metrics)
3. [Monitoring Setup](#monitoring-setup)
4. [Alerting Rules](#alerting-rules)
5. [Dashboards](#dashboards)
6. [Log Monitoring](#log-monitoring)
7. [Performance Baselines](#performance-baselines)
8. [Troubleshooting](#troubleshooting)

---

## Overview

This guide provides comprehensive monitoring strategies for the Python 3 Integration module to ensure reliability, performance, and security.

### Monitoring Goals

1. **Availability:** Ensure Python execution service is always available
2. **Performance:** Track execution times and resource usage
3. **Security:** Detect unauthorized access and suspicious activity
4. **Capacity:** Prevent pool exhaustion and resource constraints
5. **Compliance:** Monitor audit log integrity

---

## Key Metrics

### 1. Health Metrics

| Metric | Source | Healthy Range | Critical Threshold |
|--------|--------|---------------|-------------------|
| Module Status | Gateway Status | Running | Stopped/Error |
| Process Pool Health | `/api/v1/pool-stats` | healthy == totalSize | healthy < totalSize |
| Process Pool Availability | `/api/v1/pool-stats` | available > 0 | available == 0 for > 5min |
| Python Version | `/api/v1/version` | 3.11+ | < 3.11 |

**Check Command:**
```bash
curl -s http://localhost:8088/data/python3integration/api/v1/health | jq
```

**Expected Output:**
```json
{
  "healthy": true,
  "pythonVersion": "3.11.2",
  "poolSize": 3
}
```

---

### 2. Performance Metrics

| Metric | Source | Target | Warning | Critical |
|--------|--------|--------|---------|----------|
| Average Execution Time | Audit logs | < 100ms | > 500ms | > 1000ms |
| P95 Execution Time | Audit logs | < 200ms | > 1000ms | > 2000ms |
| P99 Execution Time | Audit logs | < 500ms | > 2000ms | > 5000ms |
| Request Rate | Audit logs | Variable | N/A | > 100/min (rate limit) |
| Success Rate | Audit logs | > 99% | < 95% | < 90% |

**Calculation Scripts:**
```bash
# Average execution time (last hour)
grep '"durationMs"' audit-$(date +%Y-%m-%d).log | \
  tail -1000 | \
  sed 's/.*"durationMs":\([0-9]*\).*/\1/' | \
  awk '{sum+=$1; count++} END {print "Average:", sum/count, "ms"}'

# Success rate (last hour)
RECENT=$(tail -1000 audit-$(date +%Y-%m-%d).log)
TOTAL=$(echo "$RECENT" | wc -l)
SUCCESS=$(echo "$RECENT" | grep '"success":true' | wc -l)
echo "Success rate: $(($SUCCESS * 100 / $TOTAL))%"
```

---

### 3. Resource Metrics

| Metric | Source | Target | Warning | Critical |
|--------|--------|--------|---------|----------|
| Memory Usage (per process) | OS | < 256MB | > 400MB | > 500MB |
| Total Memory Usage | OS | < 2GB | > 4GB | > 8GB |
| CPU Usage | `/api/v1/diagnostics` | < 20% | > 50% | > 80% |
| Disk Usage (logs) | OS | < 1GB | > 5GB | > 10GB |

**Check Commands:**
```bash
# Python process memory
ps aux | grep python3 | grep -v grep | \
  awk '{print $6}' | \
  awk '{sum+=$1} END {print "Total:", sum/1024, "MB"}'

# CPU usage
curl -s http://localhost:8088/data/python3integration/api/v1/diagnostics | \
  jq '.cpuUsagePercent'

# Disk usage
du -sh data/python3-integration/
```

---

### 4. Security Metrics

| Metric | Source | Target | Warning | Critical |
|--------|--------|--------|---------|----------|
| Failed Executions | Audit logs | < 1% | > 5% | > 10% |
| Security Errors | Gateway logs | 0 | > 10/day | > 50/day |
| Unauthorized Access | Audit logs | 0 | > 1/day | > 10/day |
| Invalid API Keys | Gateway logs | 0 | > 5/day | > 20/day |
| Bypass Attempts | Audit logs | Any | > 1 | > 5 |

**Check Commands:**
```bash
# Security errors today
grep "SECURITY ERROR" logs/wrapper.log | \
  grep "$(date +%Y-%m-%d)" | wc -l

# Failed executions (last hour)
tail -1000 audit-$(date +%Y-%m-%d).log | \
  grep '"success":false' | wc -l

# Bypass attempts
grep -E "(__import__|eval\(|exec\()" audit-$(date +%Y-%m-%d).log | \
  grep '"success":false' | wc -l
```

---

## Monitoring Setup

### Option 1: Basic Bash Monitoring

Create a simple monitoring script:

```bash
#!/bin/bash
# File: /opt/ignition-monitoring/python3-monitor.sh

GATEWAY="http://localhost:8088"
LOG_FILE="/var/log/python3-monitor.log"
ALERT_EMAIL="ops@example.com"

# Function: Log message
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

# Function: Send alert
alert() {
    log "ALERT: $1"
    echo "$1" | mail -s "Python3 Integration Alert" "$ALERT_EMAIL"
}

# Check 1: Module Health
health=$(curl -s "$GATEWAY/data/python3integration/api/v1/health")
if echo "$health" | jq -e '.healthy == true' > /dev/null; then
    log "Health check: OK"
else
    alert "Health check FAILED: $health"
fi

# Check 2: Pool Stats
stats=$(curl -s "$GATEWAY/data/python3integration/api/v1/pool-stats")
healthy=$(echo "$stats" | jq '.healthy')
total=$(echo "$stats" | jq '.totalSize')
available=$(echo "$stats" | jq '.available')

if [ "$healthy" -lt "$total" ]; then
    alert "Pool unhealthy: $healthy/$total processes healthy"
fi

if [ "$available" -eq 0 ]; then
    alert "Pool exhausted: 0 processes available"
fi

log "Pool stats: $healthy/$total healthy, $available available"

# Check 3: Recent Errors
error_count=$(tail -100 logs/wrapper.log | grep -c "ERROR.*Python3")
if [ "$error_count" -gt 5 ]; then
    alert "High error count: $error_count errors in last 100 log lines"
fi

log "Error count: $error_count"

# Check 4: Audit Log Integrity
today_log="data/python3-integration/audit/audit-$(date +%Y-%m-%d).log"
if [ ! -f "$today_log" ]; then
    alert "Today's audit log missing: $today_log"
else
    log "Audit log: OK"
fi

log "Monitoring check complete"
```

**Setup Cron Job:**
```bash
# Run every 5 minutes
*/5 * * * * /opt/ignition-monitoring/python3-monitor.sh

# Or more frequently (every minute)
* * * * * /opt/ignition-monitoring/python3-monitor.sh
```

---

### Option 2: Prometheus + Grafana

**Step 1: Expose Metrics Endpoint**

Create a metrics exporter script:

```python
#!/usr/bin/env python3
# File: /opt/ignition-monitoring/python3-exporter.py

from prometheus_client import start_http_server, Gauge, Counter
import requests
import time
import json

# Metrics
pool_total = Gauge('python3_pool_total', 'Total pool size')
pool_healthy = Gauge('python3_pool_healthy', 'Healthy processes')
pool_available = Gauge('python3_pool_available', 'Available processes')
pool_in_use = Gauge('python3_pool_in_use', 'Processes in use')
execution_time = Gauge('python3_execution_time_seconds', 'Average execution time')
success_rate = Gauge('python3_success_rate', 'Success rate percentage')
security_errors = Counter('python3_security_errors_total', 'Total security errors')

GATEWAY_URL = "http://localhost:8088"

def collect_metrics():
    """Collect metrics from Python 3 Integration module"""
    try:
        # Pool stats
        resp = requests.get(f"{GATEWAY_URL}/data/python3integration/api/v1/pool-stats")
        stats = resp.json()

        pool_total.set(stats['totalSize'])
        pool_healthy.set(stats['healthy'])
        pool_available.set(stats['available'])
        pool_in_use.set(stats['inUse'])

        # Diagnostics
        resp = requests.get(f"{GATEWAY_URL}/data/python3integration/api/v1/diagnostics")
        diag = resp.json()

        # Parse recent audit logs for execution time and success rate
        with open('data/python3-integration/audit/audit-{}.log'.format(
            time.strftime('%Y-%m-%d')
        )) as f:
            lines = f.readlines()[-1000:]  # Last 1000 executions

        total = len(lines)
        successful = sum(1 for line in lines if '"success":true' in line)
        avg_time = 0

        if total > 0:
            success_rate.set((successful / total) * 100)

            # Calculate average execution time
            times = []
            for line in lines:
                try:
                    data = json.loads(line)
                    if 'durationMs' in data:
                        times.append(data['durationMs'])
                except:
                    pass

            if times:
                avg_time = sum(times) / len(times) / 1000  # Convert to seconds
                execution_time.set(avg_time)

    except Exception as e:
        print(f"Error collecting metrics: {e}")

if __name__ == '__main__':
    # Start Prometheus HTTP server on port 9100
    start_http_server(9100)
    print("Python 3 Integration exporter running on port 9100")

    # Collect metrics every 30 seconds
    while True:
        collect_metrics()
        time.sleep(30)
```

**Step 2: Configure Prometheus**

Add to `prometheus.yml`:
```yaml
scrape_configs:
  - job_name: 'python3_integration'
    scrape_interval: 30s
    static_configs:
      - targets: ['localhost:9100']
```

**Step 3: Create Grafana Dashboard** (see Dashboards section below)

---

### Option 3: Nagios/Icinga

**Check Script:**
```bash
#!/bin/bash
# File: /usr/lib/nagios/plugins/check_python3_integration

GATEWAY="http://localhost:8088"
WARN_THRESHOLD=1
CRIT_THRESHOLD=2

# Check pool health
stats=$(curl -s "$GATEWAY/data/python3integration/api/v1/pool-stats")
healthy=$(echo "$stats" | jq '.healthy')
total=$(echo "$stats" | jq '.totalSize')
available=$(echo "$stats" | jq '.available')

unhealthy=$((total - healthy))

# Determine status
if [ "$unhealthy" -ge "$CRIT_THRESHOLD" ] || [ "$available" -eq 0 ]; then
    echo "CRITICAL: $unhealthy unhealthy processes, $available available"
    exit 2
elif [ "$unhealthy" -ge "$WARN_THRESHOLD" ]; then
    echo "WARNING: $unhealthy unhealthy processes, $available available"
    exit 1
else
    echo "OK: $healthy/$total healthy, $available available"
    exit 0
fi
```

**Nagios Configuration:**
```cfg
define service {
    use                     generic-service
    host_name               ignition-gateway
    service_description     Python3 Integration Health
    check_command           check_python3_integration
    check_interval          5
    retry_interval          1
    max_check_attempts      3
}
```

---

### Option 4: Splunk/ELK Stack

**Filebeat Configuration (ELK):**
```yaml
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /path/to/ignition/data/python3-integration/audit/*.log
    json.keys_under_root: true
    json.add_error_key: true
    fields:
      service: python3_integration
      environment: production

  - type: log
    enabled: true
    paths:
      - /path/to/ignition/logs/wrapper.log
    multiline.pattern: '^[0-9]{4}-[0-9]{2}-[0-9]{2}'
    multiline.negate: true
    multiline.match: after
    fields:
      service: ignition_gateway
      environment: production

output.elasticsearch:
  hosts: ["localhost:9200"]
  index: "python3-integration-%{+yyyy.MM.dd}"
```

**Splunk Configuration:**
```conf
# inputs.conf
[monitor:///path/to/ignition/data/python3-integration/audit/*.log]
sourcetype = python3_audit
index = security

[monitor:///path/to/ignition/logs/wrapper.log]
sourcetype = ignition_wrapper
index = applications
```

---

## Alerting Rules

### Critical Alerts (Immediate Response)

1. **Module Down**
   ```bash
   # Condition: Health check fails
   curl -s http://localhost:8088/data/python3integration/api/v1/health | \
     jq -e '.healthy == false'
   ```
   **Action:** Page on-call engineer

2. **Pool Exhausted**
   ```bash
   # Condition: No available processes for > 5 minutes
   available=$(curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats | jq '.available')
   [ "$available" -eq 0 ]
   ```
   **Action:** Increase pool size or restart Gateway

3. **Security Breach**
   ```bash
   # Condition: Successful bypass attempt
   grep '__import__.*success":true' audit-*.log
   ```
   **Action:** Immediate security review

4. **Disk Full**
   ```bash
   # Condition: Disk usage > 90%
   df -h | grep "9[0-9]%"
   ```
   **Action:** Clear old logs or expand disk

---

### Warning Alerts (Review Within 1 Hour)

1. **Pool Degraded**
   ```bash
   # Condition: 1+ unhealthy processes
   stats=$(curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats)
   healthy=$(echo "$stats" | jq '.healthy')
   total=$(echo "$stats" | jq '.totalSize')
   [ "$healthy" -lt "$total" ]
   ```
   **Action:** Review Gateway logs, consider restart

2. **High Error Rate**
   ```bash
   # Condition: Error rate > 5%
   # (See calculation in Key Metrics section)
   ```
   **Action:** Review failed executions in audit logs

3. **Slow Performance**
   ```bash
   # Condition: P95 execution time > 1000ms
   # (See calculation in Key Metrics section)
   ```
   **Action:** Optimize code or increase resources

4. **Certificate Expiring**
   ```bash
   # Condition: SSL certificate expires in < 30 days
   ```
   **Action:** Renew certificate

---

### Info Alerts (Daily Digest)

1. **Daily Summary**
   - Total executions
   - Success rate
   - Top users
   - Security mode distribution

2. **Resource Usage**
   - Average CPU/memory
   - Disk space trends
   - Pool utilization

3. **Audit Summary**
   - Failed executions
   - Security errors
   - Access patterns

---

## Dashboards

### Grafana Dashboard (JSON)

Save as: `python3-integration-dashboard.json`

```json
{
  "dashboard": {
    "title": "Python 3 Integration",
    "panels": [
      {
        "title": "Process Pool Health",
        "targets": [
          {"expr": "python3_pool_healthy"},
          {"expr": "python3_pool_total"}
        ],
        "type": "graph"
      },
      {
        "title": "Available Processes",
        "targets": [{"expr": "python3_pool_available"}],
        "type": "graph",
        "alert": {
          "conditions": [
            {
              "evaluator": {"params": [0], "type": "lt"},
              "query": {"params": ["A", "5m", "now"]},
              "type": "query"
            }
          ]
        }
      },
      {
        "title": "Execution Time (seconds)",
        "targets": [{"expr": "python3_execution_time_seconds"}],
        "type": "graph"
      },
      {
        "title": "Success Rate (%)",
        "targets": [{"expr": "python3_success_rate"}],
        "type": "singlestat"
      }
    ]
  }
}
```

---

### Splunk Dashboard (SPL Queries)

```spl
# Panel 1: Execution Rate Over Time
index=security sourcetype=python3_audit
| timechart count span=5m

# Panel 2: Success vs Failure
index=security sourcetype=python3_audit
| stats count by success
| eval status=if(success=="true", "Success", "Failure")

# Panel 3: Top Users
index=security sourcetype=python3_audit
| stats count by user
| sort -count
| head 10

# Panel 4: Security Mode Distribution
index=security sourcetype=python3_audit
| stats count by securityMode

# Panel 5: Average Execution Time
index=security sourcetype=python3_audit
| stats avg(durationMs) as avg_time by _time span=5m
| eval avg_time_sec=avg_time/1000

# Panel 6: Error Trends
index=security sourcetype=python3_audit success=false
| timechart count span=1h
```

---

## Log Monitoring

### Important Log Patterns

**1. Security Errors:**
```bash
# Monitor for security violations
tail -f logs/wrapper.log | grep "SECURITY ERROR"
```

**2. Pool Issues:**
```bash
# Monitor for pool problems
tail -f logs/wrapper.log | grep -E "(Pool|Process.*alive)"
```

**3. Failed Executions:**
```bash
# Monitor audit log for failures
tail -f data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | \
  jq 'select(.success == false)'
```

**4. Slow Executions:**
```bash
# Monitor for slow executions (> 5 seconds)
tail -f data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | \
  jq 'select(.durationMs > 5000)'
```

---

## Performance Baselines

Establish baselines during normal operation:

```bash
#!/bin/bash
# Create performance baseline

echo "=== Performance Baseline Report ===" > baseline-$(date +%Y-%m-%d).txt
echo "Date: $(date)" >> baseline-$(date +%Y-%m-%d).txt
echo "" >> baseline-$(date +%Y-%m-%d).txt

# Execution metrics
echo "Execution Metrics (last 1000):" >> baseline-$(date +%Y-%m-%d).txt
tail -1000 audit-$(date +%Y-%m-%d).log | \
  sed 's/.*"durationMs":\([0-9]*\).*/\1/' | \
  awk '{
    sum+=$1; sumsq+=$1*$1; count++;
    if($1>max) max=$1;
    if(min==0 || $1<min) min=$1
  } END {
    avg=sum/count;
    stddev=sqrt((sumsq/count)-(avg*avg));
    print "  Average:", avg, "ms";
    print "  Std Dev:", stddev, "ms";
    print "  Min:", min, "ms";
    print "  Max:", max, "ms"
  }' >> baseline-$(date +%Y-%m-%d).txt

# Success rate
TOTAL=$(tail -1000 audit-$(date +%Y-%m-%d).log | wc -l)
SUCCESS=$(tail -1000 audit-$(date +%Y-%m-%d).log | grep '"success":true' | wc -l)
echo "" >> baseline-$(date +%Y-%m-%d).txt
echo "Success Rate: $(($SUCCESS * 100 / $TOTAL))%" >> baseline-$(date +%Y-%m-%d).txt

# Resource usage
echo "" >> baseline-$(date +%Y-%m-%d).txt
echo "Resource Usage:" >> baseline-$(date +%Y-%m-%d).txt
echo "  Memory: $(ps aux | grep python3 | awk '{sum+=$6} END {print sum/1024 \" MB\"}')" >> baseline-$(date +%Y-%m-%d).txt
echo "  CPU: $(curl -s http://localhost:8088/data/python3integration/api/v1/diagnostics | jq '.cpuUsagePercent')%" >> baseline-$(date +%Y-%m-%d).txt

cat baseline-$(date +%Y-%m-%d).txt
```

**Typical Baselines:**
- Average execution: 50-150ms
- Success rate: > 99%
- Memory per process: 50-200MB
- CPU usage: 5-20%

---

## Troubleshooting

### High CPU Usage

**Diagnosis:**
```bash
# Find long-running Python processes
ps aux | grep python3 | sort -k3 -rn | head -5

# Check recent long executions
grep '"durationMs"' audit-*.log | \
  sed 's/.*"durationMs":\([0-9]*\).*/\1/' | \
  sort -rn | head -10
```

**Solutions:**
1. Identify slow scripts in audit logs
2. Optimize Python code
3. Increase CPU limits
4. Add more Gateway CPU cores

---

### Memory Leaks

**Diagnosis:**
```bash
# Monitor memory growth over time
while true; do
  echo "$(date): $(ps aux | grep python3 | awk '{sum+=$6} END {print sum/1024 \" MB\"}')"
  sleep 300
done
```

**Solutions:**
1. Restart Gateway (temporary fix)
2. Reduce memory limits to force process recycling
3. Review code for memory leaks
4. Update Python version

---

### Pool Exhaustion

**Diagnosis:**
```bash
# Check pool stats
curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats | jq

# Count timeout errors
grep "Timeout waiting" logs/wrapper.log | wc -l
```

**Solutions:**
1. Increase pool size
2. Optimize slow scripts
3. Reduce concurrent requests
4. Add more Gateway memory

---

**For additional troubleshooting, see:** `DESIGNER_USER_GUIDE.md` and `SECURITY_CONFIG_GUIDE.md`

---

*This guide was created for Python 3 Integration v2.6.0 - Last updated October 2025*
