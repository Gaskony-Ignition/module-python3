# Python 3 Integration - Performance Benchmarks

**Purpose:** Performance testing and baseline establishment

---

## Overview

This document provides performance benchmarking procedures and baseline metrics for the Python 3 Integration module. Use these benchmarks to:

- Establish performance baselines for your environment
- Compare performance across Gateway versions
- Identify performance regressions
- Optimize configuration (pool size, resource limits)

**Benchmark Duration:** 30-60 minutes
**Prerequisites:** Module installed and healthy
**Recommended:** Run on production-equivalent hardware

---

## Benchmark Environment

### Hardware Specifications

**Document your test environment:**

- **CPU:** ______________ (cores, GHz)
- **RAM:** ______________ GB
- **Disk:** ☐ SSD  ☐ HDD  ☐ NVMe
- **OS:** ______________ (Linux, Windows)
- **Python Version:** ______________
- **Gateway Version:** ______________
- **Pool Size:** ______________

### Configuration

```bash
# Check current configuration
grep "python3" data/ignition.conf

# Expected settings:
# wrapper.java.additional.200=-Dignition.python3.admin.apikey=<key>
# wrapper.java.additional.201=-Dignition.python3.path=/usr/bin/python3
# wrapper.java.additional.202=-Dignition.python3.poolsize=3
```

---

## Benchmark Suite

### Benchmark 1: Simple Calculation

**Objective:** Measure baseline execution overhead

```bash
#!/bin/bash
echo "Benchmark 1: Simple Calculation (2 + 2)"
echo "============================================"

# Run 100 iterations
TIMES=()
for i in {1..100}; do
  START=$(date +%s%N)
  RESULT=$(curl -s -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "result = 2 + 2"}' | jq -r '.result')
  END=$(date +%s%N)

  # Calculate milliseconds
  TIME=$(( ($END - $START) / 1000000 ))
  TIMES+=($TIME)

  # Progress indicator
  if [ $(($i % 10)) -eq 0 ]; then
    echo "Progress: $i/100"
  fi
done

# Calculate statistics
printf '%s\n' "${TIMES[@]}" | awk '
{
  sum += $1
  if (NR == 1 || $1 < min) min = $1
  if (NR == 1 || $1 > max) max = $1
  times[NR] = $1
}
END {
  avg = sum / NR

  # Calculate median
  n = asort(times)
  if (n % 2) {
    median = times[(n+1)/2]
  } else {
    median = (times[n/2] + times[n/2+1]) / 2
  }

  # Calculate P95
  p95_idx = int(n * 0.95)
  p95 = times[p95_idx]

  print "Results (100 iterations):"
  print "  Average: " avg " ms"
  print "  Median:  " median " ms"
  print "  Min:     " min " ms"
  print "  Max:     " max " ms"
  print "  P95:     " p95 " ms"
}
'
```

**Expected Results:**
- **Average:** < 100ms
- **Median:** < 80ms
- **P95:** < 150ms

**Your Results:**
- Average: _______ ms
- Median: _______ ms
- P95: _______ ms

---

### Benchmark 2: Module Import Overhead

**Objective:** Measure cost of importing standard modules

```bash
#!/bin/bash
echo "Benchmark 2: Module Import Overhead"
echo "============================================"

# Test different import scenarios
declare -A IMPORT_TESTS=(
  ["Single module"]="import math; result = None"
  ["Three modules"]="import math, json, datetime; result = None"
  ["Five modules"]="import math, json, datetime, re, random; result = None"
  ["Complex imports"]="import math; import json; import datetime; from collections import Counter; result = None"
)

for test_name in "${!IMPORT_TESTS[@]}"; do
  code="${IMPORT_TESTS[$test_name]}"
  echo ""
  echo "Test: $test_name"

  TOTAL=0
  for i in {1..50}; do
    START=$(date +%s%N)
    curl -s -X POST http://localhost:8088/data/python3integration/api/v1/exec \
      -H "Content-Type: application/json" \
      -d "{\"code\": \"$code\"}" > /dev/null
    END=$(date +%s%N)

    TIME=$(( ($END - $START) / 1000000 ))
    TOTAL=$(($TOTAL + $TIME))
  done

  AVG=$(($TOTAL / 50))
  echo "  Average: $AVG ms"
done
```

**Expected Results:**
- Single module: < 100ms
- Three modules: < 120ms
- Five modules: < 150ms
- Complex imports: < 180ms

**Your Results:**
- Single module: _______ ms
- Three modules: _______ ms
- Five modules: _______ ms
- Complex imports: _______ ms

---

### Benchmark 3: Computation Complexity

**Objective:** Measure performance across different workload types

```bash
#!/bin/bash
echo "Benchmark 3: Computation Complexity"
echo "============================================"

# Test computations of varying complexity
declare -A COMPUTE_TESTS=(
  ["Arithmetic"]="result = sum(range(1000))"
  ["Factorial"]="import math; result = math.factorial(100)"
  ["List comprehension"]="result = sum([i**2 for i in range(1000)])"
  ["String manipulation"]="result = ''.join([str(i) for i in range(1000)])"
  ["JSON processing"]="import json; data = {str(i): i for i in range(100)}; result = json.dumps(data)"
)

for test_name in "${!COMPUTE_TESTS[@]}"; do
  code="${COMPUTE_TESTS[$test_name]}"
  echo ""
  echo "Test: $test_name"

  TOTAL=0
  for i in {1..50}; do
    START=$(date +%s%N)
    curl -s -X POST http://localhost:8088/data/python3integration/api/v1/exec \
      -H "Content-Type: application/json" \
      -d "{\"code\": \"$code\"}" > /dev/null
    END=$(date +%s%N)

    TIME=$(( ($END - $START) / 1000000 ))
    TOTAL=$(($TOTAL + $TIME))
  done

  AVG=$(($TOTAL / 50))
  echo "  Average: $AVG ms"
done
```

**Expected Results:**
- Arithmetic: < 100ms
- Factorial: < 120ms
- List comprehension: < 150ms
- String manipulation: < 180ms
- JSON processing: < 200ms

**Your Results:**
- Arithmetic: _______ ms
- Factorial: _______ ms
- List comprehension: _______ ms
- String manipulation: _______ ms
- JSON processing: _______ ms

---

### Benchmark 4: Concurrent Execution

**Objective:** Measure throughput under concurrent load

```bash
#!/bin/bash
echo "Benchmark 4: Concurrent Execution"
echo "============================================"

# Test concurrent requests (pool size = 3)
CODE='{"code": "import math; result = math.sqrt(16)"}'

for concurrency in 1 3 5 10 20; do
  echo ""
  echo "Concurrency: $concurrency"

  START=$(date +%s%N)

  for i in $(seq 1 $concurrency); do
    curl -s -X POST http://localhost:8088/data/python3integration/api/v1/exec \
      -H "Content-Type: application/json" \
      -d "$CODE" > /dev/null &
  done

  wait

  END=$(date +%s%N)
  TIME=$(( ($END - $START) / 1000000 ))
  THROUGHPUT=$(echo "scale=2; $concurrency * 1000 / $TIME" | bc)

  echo "  Total time: $TIME ms"
  echo "  Throughput: $THROUGHPUT req/s"
done
```

**Expected Results:**
- Concurrency 1: ~10 req/s
- Concurrency 3: ~25 req/s (pool size)
- Concurrency 5: ~25 req/s (limited by pool)
- Concurrency 10: ~25 req/s (queueing)
- Concurrency 20: ~25 req/s (queueing)

**Your Results:**
- Concurrency 1: _______ req/s
- Concurrency 3: _______ req/s
- Concurrency 5: _______ req/s
- Concurrency 10: _______ req/s
- Concurrency 20: _______ req/s

---

### Benchmark 5: Security Validation Overhead

**Objective:** Measure AST validation performance impact

> **Not applicable.** All authenticated callers (ADMIN, DESIGNER_ADMIN) take the same code path, so there is no per-mode overhead to benchmark. The "AST validation overhead" line stays at zero by construction.
>
> To benchmark authenticated vs. unauthenticated performance:
>
> ```bash
> ADMIN_KEY="<your-admin-key>"
> for i in {1..50}; do
>   curl -s -X POST https://localhost:8088/data/python3integration/api/v1/exec \
>     -H "Authorization: Bearer $ADMIN_KEY" \
>     -H "Content-Type: application/json" \
>     -d '{"code": "import math; result = math.sqrt(16)"}' > /dev/null
> done
> ```
>
> Unauthenticated calls return immediately with `403 Forbidden` (no Python execution) and aren't a useful comparison point.
- AST overhead: N/A — layer removed in v4.0.0, no longer benchmarked

---

### Benchmark 6: Audit Logging Overhead

**Objective:** Measure performance impact of audit logging

```bash
#!/bin/bash
echo "Benchmark 6: Audit Logging Overhead"
echo "============================================"

# Clear audit log for accurate measurement
> data/python3-integration/audit/audit-$(date +%Y-%m-%d).log

# Run 100 executions and measure total time
START=$(date +%s%N)

for i in {1..100}; do
  curl -s -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "result = 2 + 2"}' > /dev/null
done

END=$(date +%s%N)
TOTAL_TIME=$(( ($END - $START) / 1000000 ))
AVG_TIME=$(($TOTAL_TIME / 100))

# Check audit log size
LOG_SIZE=$(wc -l < data/python3-integration/audit/audit-$(date +%Y-%m-%d).log)
LOG_BYTES=$(stat -c%s data/python3-integration/audit/audit-$(date +%Y-%m-%d).log)

echo "Results:"
echo "  Total executions: 100"
echo "  Total time: $TOTAL_TIME ms"
echo "  Average time: $AVG_TIME ms"
echo "  Audit entries: $LOG_SIZE"
echo "  Log size: $LOG_BYTES bytes"
echo "  Bytes per entry: $(($LOG_BYTES / $LOG_SIZE))"
```

**Expected Results:**
- Average time: < 100ms (similar to Benchmark 1)
- Audit entries: 100 (all logged)
- Bytes per entry: ~200-300 bytes

**Your Results:**
- Average time: _______ ms
- Audit entries: _______
- Bytes per entry: _______ bytes

---

### Benchmark 7: Pool Scalability

**Objective:** Determine optimal pool size for your environment

```bash
#!/bin/bash
echo "Benchmark 7: Pool Scalability"
echo "============================================"
echo "NOTE: This test requires Gateway restart with different pool sizes"
echo ""

# Test pool sizes: 1, 3, 5, 10, 20
# For each size:
# 1. Edit ignition.conf: wrapper.java.additional.202=-Dignition.python3.poolsize=N
# 2. Restart Gateway
# 3. Run concurrent load test

for pool_size in 1 3 5 10 20; do
  echo "Pool Size: $pool_size"
  echo "  (Edit ignition.conf and restart Gateway, then press Enter)"
  read

  # Run 100 concurrent requests
  START=$(date +%s%N)

  for i in {1..100}; do
    curl -s -X POST http://localhost:8088/data/python3integration/api/v1/exec \
      -H "Content-Type: application/json" \
      -d '{"code": "import math; result = math.sqrt(16)"}' > /dev/null &
  done

  wait

  END=$(date +%s%N)
  TOTAL_TIME=$(( ($END - $START) / 1000000 ))
  THROUGHPUT=$(echo "scale=2; 100000 / $TOTAL_TIME" | bc)

  echo "  Total time: $TOTAL_TIME ms"
  echo "  Throughput: $THROUGHPUT req/s"
  echo ""
done
```

**Expected Results:**
- Pool size 1: ~10 req/s (sequential)
- Pool size 3: ~30 req/s (default)
- Pool size 5: ~50 req/s
- Pool size 10: ~80 req/s (diminishing returns)
- Pool size 20: ~90 req/s (overhead increases)

**Your Results:**
- Pool size 1: _______ req/s
- Pool size 3: _______ req/s
- Pool size 5: _______ req/s
- Pool size 10: _______ req/s
- Pool size 20: _______ req/s

**Recommended Pool Size:** _______ (balance throughput vs resource usage)

---

### Benchmark 8: Memory Usage

**Objective:** Measure memory consumption per process and total

```bash
#!/bin/bash
echo "Benchmark 8: Memory Usage"
echo "============================================"

# Get Python process memory
echo "Python Process Memory:"
ps aux | grep python_bridge.py | grep -v grep | awk '{
  sum += $6
  count++
  print "  Process " count ": " $6 " KB (" $6/1024 " MB)"
}
END {
  if (count > 0) {
    print "  Total: " sum " KB (" sum/1024 " MB)"
    print "  Average per process: " sum/count " KB (" sum/count/1024 " MB)"
  }
}'

# Get Gateway JVM memory
echo ""
echo "Gateway Memory (via diagnostics API):"
curl -s http://localhost:8088/data/python3integration/api/v1/diagnostics | \
  jq -r '"  Module memory: " + (.memoryUsageMB|tostring) + " MB"'

# Monitor memory over time (30 executions)
echo ""
echo "Memory stability test (30 executions):"
for i in {1..30}; do
  curl -s -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "x = list(range(10000)); result = len(x)"}' > /dev/null

  if [ $(($i % 10)) -eq 0 ]; then
    MEM=$(ps aux | grep python_bridge.py | grep -v grep | awk '{sum+=$6} END {print sum/1024}')
    echo "  After $i executions: $MEM MB"
  fi
done
```

**Expected Results:**
- Per-process memory: < 100 MB
- Total Python memory (pool size 3): < 300 MB
- Memory stability: No increase over 30 executions (no leaks)

**Your Results:**
- Per-process memory: _______ MB
- Total Python memory: _______ MB
- Memory after 30 executions: _______ MB

---

### Benchmark 9: Startup Time

**Objective:** Measure module initialization time

```bash
#!/bin/bash
echo "Benchmark 9: Startup Time"
echo "============================================"

# Restart Gateway and measure time to "healthy"
echo "Restarting Gateway..."
./gwcmd.sh -r

START=$(date +%s)

# Wait for Gateway to be available
while ! curl -s http://localhost:8088/StatusPing > /dev/null; do
  sleep 1
done

GATEWAY_UP=$(date +%s)
GATEWAY_TIME=$(($GATEWAY_UP - $START))
echo "Gateway available: $GATEWAY_TIME seconds"

# Wait for Python3 module to be healthy
while true; do
  HEALTH=$(curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats | jq -r '.healthy')
  TOTAL=$(curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats | jq -r '.totalSize')

  if [ "$HEALTH" = "$TOTAL" ] && [ "$HEALTH" != "null" ]; then
    break
  fi
  sleep 1
done

MODULE_UP=$(date +%s)
MODULE_TIME=$(($MODULE_UP - $START))
echo "Python3 module healthy: $MODULE_TIME seconds"
echo "Module initialization time: $(($MODULE_TIME - $GATEWAY_TIME)) seconds"
```

**Expected Results:**
- Gateway startup: 20-40 seconds
- Module initialization: 5-10 seconds
- Total to healthy: 25-50 seconds

**Your Results:**
- Gateway startup: _______ seconds
- Module initialization: _______ seconds
- Total: _______ seconds

---

### Benchmark 10: Sustained Load

**Objective:** Measure performance under sustained production-like load

```bash
#!/bin/bash
echo "Benchmark 10: Sustained Load (5 minutes)"
echo "============================================"

# Run sustained load for 5 minutes
DURATION=300  # 5 minutes
RATE=10       # 10 req/s target

echo "Running $RATE req/s for $DURATION seconds..."

START=$(date +%s)
COUNT=0
ERRORS=0

while [ $(( $(date +%s) - $START )) -lt $DURATION ]; do
  # Send request
  RESULT=$(curl -s -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "result = 2 + 2"}')

  if echo "$RESULT" | jq -e '.success == true' > /dev/null 2>&1; then
    ((COUNT++))
  else
    ((ERRORS++))
  fi

  # Rate limiting (sleep to maintain ~10 req/s)
  sleep 0.1

  # Progress update every 60 seconds
  ELAPSED=$(( $(date +%s) - $START ))
  if [ $(($ELAPSED % 60)) -eq 0 ] && [ $ELAPSED -gt 0 ]; then
    RATE_ACTUAL=$(echo "scale=2; $COUNT / $ELAPSED" | bc)
    echo "  $ELAPSED seconds: $COUNT successful, $ERRORS failed, $RATE_ACTUAL req/s"
  fi
done

END=$(date +%s)
TOTAL_TIME=$(($END - $START))
FINAL_RATE=$(echo "scale=2; $COUNT / $TOTAL_TIME" | bc)
ERROR_RATE=$(echo "scale=2; $ERRORS * 100 / ($COUNT + $ERRORS)" | bc)

echo ""
echo "Results:"
echo "  Duration: $TOTAL_TIME seconds"
echo "  Successful: $COUNT"
echo "  Failed: $ERRORS"
echo "  Average rate: $FINAL_RATE req/s"
echo "  Error rate: $ERROR_RATE%"

# Check final pool stats
echo ""
echo "Final Pool Stats:"
curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats | jq
```

**Expected Results:**
- Successful executions: ~3,000 (10 req/s × 300s)
- Error rate: < 0.1%
- Average rate: ~10 req/s (matching target)
- Final pool: All processes healthy

**Your Results:**
- Successful: _______
- Failed: _______
- Average rate: _______ req/s
- Error rate: _______ %

---

## Automated Benchmark Runner

Complete benchmark script for automated execution:

```bash
#!/bin/bash
# performance-benchmark.sh - Complete automated benchmark suite

set -e

GATEWAY_URL="http://localhost:8088"
RESULTS_FILE="benchmark-results-$(date +%Y-%m-%d-%H%M%S).txt"

exec > >(tee -a "$RESULTS_FILE")
exec 2>&1

echo "======================================================="
echo "Python 3 Integration - Performance Benchmark Suite"
echo "Version: 2.6.0"
echo "Date: $(date)"
echo "======================================================="
echo ""

# Environment info
echo "Environment:"
echo "  OS: $(uname -s)"
echo "  Kernel: $(uname -r)"
echo "  CPU: $(grep "model name" /proc/cpuinfo | head -1 | cut -d: -f2 | xargs)"
echo "  CPU Cores: $(nproc)"
echo "  RAM: $(free -h | grep Mem | awk '{print $2}')"
echo "  Python: $(python3 --version)"
echo ""

# Check Gateway health
echo "Gateway Health Check:"
HEALTH=$(curl -s $GATEWAY_URL/data/python3integration/api/v1/health)
echo "$HEALTH" | jq
echo ""

# Benchmark 1: Simple Calculation
echo "Benchmark 1: Simple Calculation"
echo "----------------------------------------------------"
TIMES=()
for i in {1..100}; do
  START=$(date +%s%N)
  curl -s -X POST $GATEWAY_URL/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "result = 2 + 2"}' > /dev/null
  END=$(date +%s%N)
  TIME=$(( ($END - $START) / 1000000 ))
  TIMES+=($TIME)
done

printf '%s\n' "${TIMES[@]}" | awk '
{
  sum += $1
  if (NR == 1 || $1 < min) min = $1
  if (NR == 1 || $1 > max) max = $1
  times[NR] = $1
}
END {
  avg = sum / NR
  n = asort(times)
  median = times[int(n/2)]
  p95 = times[int(n*0.95)]

  print "  Average: " avg " ms"
  print "  Median:  " median " ms"
  print "  Min:     " min " ms"
  print "  Max:     " max " ms"
  print "  P95:     " p95 " ms"
}
'
echo ""

# Benchmark 2: Concurrent Load
echo "Benchmark 2: Concurrent Load (20 concurrent)"
echo "----------------------------------------------------"
START=$(date +%s%N)
for i in {1..20}; do
  curl -s -X POST $GATEWAY_URL/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "import math; result = math.sqrt(16)"}' > /dev/null &
done
wait
END=$(date +%s%N)
TIME=$(( ($END - $START) / 1000000 ))
THROUGHPUT=$(echo "scale=2; 20000 / $TIME" | bc)
echo "  Total time: $TIME ms"
echo "  Throughput: $THROUGHPUT req/s"
echo ""

# Benchmark 3: Memory Usage
echo "Benchmark 3: Memory Usage"
echo "----------------------------------------------------"
ps aux | grep python_bridge.py | grep -v grep | awk '
{
  sum += $6
  count++
}
END {
  if (count > 0) {
    print "  Total memory: " sum/1024 " MB"
    print "  Per process: " sum/count/1024 " MB"
    print "  Process count: " count
  }
}
'
echo ""

# Final pool stats
echo "Final Pool Statistics:"
echo "----------------------------------------------------"
curl -s $GATEWAY_URL/data/python3integration/api/v1/pool-stats | jq
echo ""

echo "======================================================="
echo "Benchmark complete. Results saved to: $RESULTS_FILE"
echo "======================================================="
```

Save as `performance-benchmark.sh`, make executable, and run:

```bash
chmod +x performance-benchmark.sh
./performance-benchmark.sh
```

---

## Benchmark Results Template

**Date:** ______________

**Tester:** ______________

**Environment:** ______________

### Summary Table

| Benchmark | Metric | Expected | Actual | Status |
|-----------|--------|----------|--------|--------|
| Simple Calculation | Average | < 100ms | ___ ms | ☐ Pass ☐ Fail |
| Module Import | Average | < 150ms | ___ ms | ☐ Pass ☐ Fail |
| Computation | Average | < 200ms | ___ ms | ☐ Pass ☐ Fail |
| Concurrent (20) | Throughput | ~25 req/s | ___ req/s | ☐ Pass ☐ Fail |
| AST Validation | Overhead | N/A — removed v4.0.0 | N/A | N/A |
| Audit Logging | Overhead | < 5% | ___% | ☐ Pass ☐ Fail |
| Memory | Per Process | < 100 MB | ___ MB | ☐ Pass ☐ Fail |
| Startup | Module Init | < 10s | ___ s | ☐ Pass ☐ Fail |
| Sustained Load | Error Rate | < 0.1% | ___% | ☐ Pass ☐ Fail |

### Performance Grade

**Overall Performance:** ☐ Excellent  ☐ Good  ☐ Acceptable  ☐ Needs Tuning

**Recommended Actions:**
_____________________________________________________________________________________
_____________________________________________________________________________________

---

*This benchmark suite was created for Python 3 Integration v2.6.0 - Last updated October 2025*
