# Python 3 Integration - End-to-End Test Suite

**Purpose:** Comprehensive end-to-end testing for Phase 4 completion

---

## Overview

This test suite validates the complete Python 3 Integration module from installation through production operation. It covers all three security modes, AST validation, Designer IDE integration, REST API functionality, and operational procedures.

**Test Duration:** 3-4 hours
**Prerequisites:** Clean Ignition 8.3 Gateway installation
**Test Environment:** Development/Staging gateway (not production)

---

## Test Environment Setup

### Prerequisites

- Ignition 8.3+ Gateway installed and running
- Python 3.11+ installed on Gateway server
- Gateway accessible on ports 8088 (HTTP) and 8043 (HTTPS)
- Ignition Designer installed
- curl, jq, python3 available in PATH

### Installation

1. **Build Module:**
   ```bash
   cd /modules/ignition-module-python3/python3-integration
   ./gradlew clean build --no-daemon
   ```

2. **Install Module:**
   - Navigate to: http://localhost:8088 → Config → System → Modules
   - Click "Install or Upgrade a Module"
   - Upload: `build/libs/Python3Integration-2.6.0.modl`
   - Restart Gateway

3. **Configure Admin API Key:**
   ```bash
   # Generate 64-character key
   ADMIN_KEY=$(openssl rand -hex 32)
   echo "Admin Key: $ADMIN_KEY"

   # Add to ignition.conf
   echo "wrapper.java.additional.200=-Dignition.python3.admin.apikey=$ADMIN_KEY" >> data/ignition.conf

   # Restart Gateway
   ./gwcmd.sh -r
   ```

4. **Verify Installation:**
   ```bash
   # Check module loaded
   curl http://localhost:8088/data/python3integration/api/v1/health | jq

   # Expected output:
   # {
   #   "status": "healthy",
   #   "version": "2.6.0",
   #   "pythonVersion": "3.11.2",
   #   "poolSize": 3
   # }
   ```

---

## Test Suite 1: Security Mode Validation

### Test 1.1: Unauthenticated requests rejected (v4.0.0+)

**Objective:** Verify the gateway rejects anonymous REST calls with `403 Forbidden`. The previous RESTRICTED mode that allowed unauthenticated callers to execute "safe" modules was removed in v4.0.0.

```bash
# Test 1.1.1: Unauthenticated request — should be rejected regardless of payload
curl -i -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import math; result = math.sqrt(16)"
  }'

# Expected: HTTP/1.1 403 Forbidden
# Body: {"success": false, "error": "Authentication required..."}

# Test 1.1.2: Same payload with a valid admin key — should succeed
curl -X POST https://localhost:8088/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import math; result = math.sqrt(16)"
  }' | jq

# Expected: {"success": true, "result": 4.0}
```

**Pass Criteria:**
- ✅ All unauthenticated calls return `403 Forbidden`, regardless of payload safety
- ✅ The audit log records the rejection at `WARN` level with `"securityMode": "DENIED"`
- ✅ No Python subprocess is invoked for rejected requests (verify pool stats unchanged)

---

### Test 1.2: ADMIN Mode (API Key Authentication)

**Objective:** Verify API key authentication grants ADMIN mode with extended module access

```bash
# Replace <ADMIN_KEY> with your generated key
ADMIN_KEY="<your-64-char-key>"

# Test 1.2.1: Admin module access via Bearer token (should succeed)
curl -X POST https://localhost:8043/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import os; result = os.getcwd()"
  }' | jq

# Expected: {"success": true, "result": "/path/to/ignition"}

# Test 1.2.2: Admin module access via X-Python3-Admin-Key (should succeed)
curl -X POST https://localhost:8043/data/python3integration/api/v1/exec \
  -H "X-Python3-Admin-Key: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import subprocess; result = \"test\""
  }' | jq

# Expected: {"success": true, "result": "test"}

# Test 1.2.3: Invalid API key (should fail)
curl -X POST https://localhost:8043/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer invalid-key-123" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import os; result = os.getcwd()"
  }' | jq

# Expected: {"success": false, "error": "Invalid admin API key"}

# Test 1.2.4: HTTPS enforcement (should fail over HTTP)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import os; result = os.getcwd()"
  }' | jq

# Expected: {"success": false, "error": "HTTPS required for ADMIN mode"}
```

**Pass Criteria:**
- ✅ Valid API key grants ADMIN mode access
- ✅ Extended modules (os, sys, subprocess) execute successfully
- ✅ Invalid API key rejected with error
- ✅ HTTPS enforced for ADMIN mode (HTTP requests fail)
- ✅ Audit log shows `"securityMode": "ADMIN"`

---

### Test 1.3: DESIGNER_ADMIN Mode (Designer IDE)

**Objective:** Verify Designer IDE requests automatically receive DESIGNER_ADMIN mode

```bash
# Test 1.3.1: Designer User-Agent grants DESIGNER_ADMIN (should succeed)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "User-Agent: Ignition-Designer/8.3" \
  -H "X-Source: Python3-IDE" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import os; result = os.getcwd()"
  }' | jq

# Expected: {"success": true, "result": "/path/to/ignition"}

# Test 1.3.2: Case-insensitive User-Agent (should succeed)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "User-Agent: ignition-designer/8.3" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import sys; result = sys.version"
  }' | jq

# Expected: {"success": true, "result": "3.11.2 (main, ...)"}

# Test 1.3.3: Full Python capabilities (should succeed)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "User-Agent: Ignition-Designer/8.3" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import os, sys, subprocess; result = \"full access\""
  }' | jq

# Expected: {"success": true, "result": "full access"}

# Test 1.3.4: AST validation bypassed (should succeed)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "User-Agent: Ignition-Designer/8.3" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "eval(\"2 + 2\")"
  }' | jq

# Expected: {"success": true} (no AST blocking for DESIGNER_ADMIN)
```

**Pass Criteria:**
- ✅ User-Agent header detection grants DESIGNER_ADMIN mode
- ✅ Case-insensitive detection works
- ✅ All Python modules accessible (os, sys, subprocess, etc.)
- ✅ AST validation skipped for Designer users
- ✅ Audit log shows `"securityMode": "DESIGNER_ADMIN"`

---

## Test Suite 2: AST Validation Security

### Test 2.1: Bypass Prevention

**Objective:** Verify AST validation blocks all known bypass techniques

```bash
# Test 2.1.1: String concatenation bypass (should fail)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "eval(\"im\" + \"port os\")"
  }' | jq

# Expected: {"success": false, "error": "Dangerous function 'eval' detected"}

# Test 2.1.2: Dynamic __import__ bypass (should fail)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "__import__(\"os\").system(\"ls\")"
  }' | jq

# Expected: {"success": false, "error": "Dangerous function '__import__' detected"}

# Test 2.1.3: Getattr bypass (should fail)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "getattr(__builtins__, \"eval\")(\"import os\")"
  }' | jq

# Expected: {"success": false, "error": "Dangerous function 'eval' detected"}

# Test 2.1.4: Lambda bypass (should fail)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "(lambda: __import__(\"os\"))()"
  }' | jq

# Expected: {"success": false, "error": "Dangerous function '__import__' detected"}

# Test 2.1.5: Base64 encoding bypass (should fail)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import base64; exec(base64.b64decode(b\"aW1wb3J0IG9z\"))"
  }' | jq

# Expected: {"success": false, "error": "Dangerous function 'exec' detected"}
```

**Pass Criteria:**
- ✅ All bypass attempts blocked by AST validation
- ✅ Error messages indicate security violation
- ✅ Audit logs show failed attempts with bypass techniques
- ✅ No successful evasion of security restrictions

---

### Test 2.2: Always-Blocked Modules

**Objective:** Verify critical modules blocked in all modes except DESIGNER_ADMIN

```bash
# Test 2.2.1: ctypes blocked (should fail)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import ctypes; result = None"
  }' | jq

# Expected: {"success": false, "error": "Module 'ctypes' is always blocked"}

# Test 2.2.2: multiprocessing blocked (should fail)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import multiprocessing; result = None"
  }' | jq

# Expected: {"success": false, "error": "Module 'multiprocessing' is always blocked"}

# Test 2.2.3: threading blocked (should fail)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import threading; result = None"
  }' | jq

# Expected: {"success": false, "error": "Module 'threading' is always blocked"}

# Test 2.2.4: Designer can use blocked modules (should succeed)
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "User-Agent: Ignition-Designer/8.3" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import ctypes; result = \"allowed for Designer\""
  }' | jq

# Expected: {"success": true, "result": "allowed for Designer"}
```

**Pass Criteria:**
- ✅ ctypes, multiprocessing, threading blocked in RESTRICTED mode
- ✅ Always-blocked modules blocked in ADMIN mode
- ✅ Designer users (DESIGNER_ADMIN) can use all modules
- ✅ Clear error messages indicating always-blocked status

---

## Test Suite 3: Designer IDE Integration

### Test 3.1: Designer IDE Manual Testing

**Objective:** Verify Designer IDE functionality and DESIGNER_ADMIN mode

**Steps:**

1. **Open Designer IDE:**
   - Launch Ignition Designer
   - Navigate to: Tools → Python 3 IDE
   - IDE window should open

2. **Connect to Gateway:**
   - Gateway URL should auto-populate (http://localhost:8088)
   - Click "Connect" button
   - Status should show: "✓ Connected"

3. **Test Execution (RESTRICTED-like code):**
   ```python
   import math
   import json
   import datetime

   result = {
       "sqrt": math.sqrt(16),
       "now": str(datetime.datetime.now()),
       "json": json.dumps({"test": "data"})
   }
   ```
   - Click "Execute" button
   - Output tab should show: `{'sqrt': 4.0, 'now': '2025-10-20...', 'json': '{"test": "data"}'}`
   - Status: "✓ Execution completed in X ms"

4. **Test Extended Modules (DESIGNER_ADMIN privilege):**
   ```python
   import os
   import sys
   import subprocess

   result = {
       "cwd": os.getcwd(),
       "python": sys.version,
       "test": "Designer has full access"
   }
   ```
   - Click "Execute" button
   - Output tab should show directory path, Python version
   - No security errors (DESIGNER_ADMIN mode)

5. **Test Script Management:**
   - Enter script name: "Test Script 1"
   - Enter description: "End-to-end test script"
   - Click "Save Script" button
   - Script tree should refresh, showing "Test Script 1"
   - Right-click script → "Load"
   - Code editor should populate with saved code

6. **Test Diagnostics Panel:**
   - After execution, check diagnostics panel
   - Should show: Execution time, Pool stats, Memory usage
   - Pool stats: Healthy > 0, Available ≥ 0

**Pass Criteria:**
- ✅ IDE opens and connects to Gateway
- ✅ Code executes successfully with output display
- ✅ Extended modules (os, sys) work without API key
- ✅ Script save/load functionality works
- ✅ Diagnostics show accurate metrics
- ✅ No security blocking for Designer users

---

### Test 3.2: Designer Headers Validation

**Objective:** Verify Designer REST client sends correct headers

**Steps:**

1. **Enable Gateway Logging:**
   ```bash
   # Add to data/ignition.conf
   wrapper.java.additional.300=-DPYTHON3_DEBUG=true
   ./gwcmd.sh -r
   ```

2. **Execute from Designer IDE:**
   - Open Designer → Tools → Python 3 IDE
   - Execute simple code: `result = 2 + 2`

3. **Check Gateway Logs:**
   ```bash
   grep "Python3" logs/wrapper.log | tail -20
   ```

4. **Verify Headers:**
   - Logs should show: `User-Agent: Ignition-Designer/8.3`
   - Logs should show: `X-Source: Python3-IDE`
   - Security mode should be: `DESIGNER_ADMIN`

**Pass Criteria:**
- ✅ User-Agent header sent correctly
- ✅ X-Source header sent correctly
- ✅ DESIGNER_ADMIN mode detected
- ✅ No API key required for Designer

---

## Test Suite 4: REST API Endpoints

### Test 4.1: Health & Diagnostics Endpoints

**Objective:** Verify all GET endpoints return correct data

```bash
# Test 4.1.1: Health check
curl http://localhost:8088/data/python3integration/api/v1/health | jq

# Expected:
# {
#   "status": "healthy",
#   "version": "2.6.0",
#   "pythonVersion": "3.11.2",
#   "poolSize": 3
# }

# Test 4.1.2: Python version
curl http://localhost:8088/data/python3integration/api/v1/version | jq

# Expected:
# {
#   "version": "3.11.2",
#   "fullVersion": "3.11.2 (main, ...)"
# }

# Test 4.1.3: Pool statistics
curl http://localhost:8088/data/python3integration/api/v1/pool-stats | jq

# Expected:
# {
#   "totalSize": 3,
#   "healthy": 3,
#   "available": 3,
#   "inUse": 0
# }

# Test 4.1.4: Diagnostics
curl http://localhost:8088/data/python3integration/api/v1/diagnostics | jq

# Expected:
# {
#   "poolStats": {...},
#   "memoryUsageMB": 128.5,
#   "cpuUsagePercent": 5.2,
#   "executionMetrics": {...}
# }

# Test 4.1.5: Example endpoint
curl http://localhost:8088/data/python3integration/api/v1/example | jq

# Expected:
# {
#   "message": "Python 3 Integration API is working!",
#   "example": 42
# }
```

**Pass Criteria:**
- ✅ All GET endpoints return 200 OK
- ✅ JSON responses valid and complete
- ✅ Version numbers match module version (2.6.0)
- ✅ Pool stats show all processes healthy

---

### Test 4.2: Execution Endpoints

**Objective:** Verify POST endpoints execute code correctly

```bash
# Test 4.2.1: Execute endpoint
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "x = 10; y = 20; result = x + y"
  }' | jq

# Expected: {"success": true, "result": 30}

# Test 4.2.2: Evaluate endpoint
curl -X POST http://localhost:8088/data/python3integration/api/v1/eval \
  -H "Content-Type: application/json" \
  -d '{
    "expression": "2 ** 10",
    "variables": {}
  }' | jq

# Expected: {"success": true, "result": 1024}

# Test 4.2.3: Call module endpoint
curl -X POST http://localhost:8088/data/python3integration/api/v1/call-module \
  -H "Content-Type: application/json" \
  -d '{
    "module": "math",
    "function": "factorial",
    "args": [5]
  }' | jq

# Expected: {"success": true, "result": 120}

# Test 4.2.4: Variables passed correctly
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "result = name + \" is \" + str(age) + \" years old\"",
    "variables": {"name": "Alice", "age": 30}
  }' | jq

# Expected: {"success": true, "result": "Alice is 30 years old"}
```

**Pass Criteria:**
- ✅ All execution endpoints return correct results
- ✅ Variables passed correctly to Python code
- ✅ Module calls work as expected
- ✅ Response format consistent (success, result fields)

---

## Test Suite 5: Audit Logging

### Test 5.1: Audit Log Format

**Objective:** Verify audit logs capture all required fields

```bash
# Test 5.1.1: Execute test request
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "result = 2 + 2"
  }'

# Test 5.1.2: Check audit log
tail -1 data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | jq

# Expected fields:
# {
#   "timestamp": "2025-10-20T10:30:45.123Z",
#   "user": "admin" or null,
#   "sourceIP": "127.0.0.1",
#   "endpoint": "/api/v1/exec",
#   "securityMode": "RESTRICTED",
#   "success": true,
#   "durationMs": 145,
#   "codeHash": "abc123...",
#   "error": null
# }
```

**Pass Criteria:**
- ✅ Log file exists: `data/python3-integration/audit/audit-YYYY-MM-DD.log`
- ✅ One-line JSON format (no newlines)
- ✅ All required fields present
- ✅ Security mode logged correctly
- ✅ Success/failure status accurate

---

### Test 5.2: Audit Log Content

**Objective:** Verify audit logs capture different security modes

```bash
# Test 5.2.1: RESTRICTED mode execution
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{"code": "import math; result = math.sqrt(16)"}'

# Test 5.2.2: ADMIN mode execution
curl -X POST https://localhost:8043/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"code": "import os; result = os.getcwd()"}'

# Test 5.2.3: DESIGNER_ADMIN mode execution
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "User-Agent: Ignition-Designer/8.3" \
  -H "Content-Type: application/json" \
  -d '{"code": "import sys; result = sys.version"}'

# Test 5.2.4: Check all three modes logged
cat data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | jq -r '.securityMode' | sort | uniq -c

# Expected output:
# 1 ADMIN
# 1 DESIGNER_ADMIN
# 1 RESTRICTED
```

**Pass Criteria:**
- ✅ RESTRICTED mode logged correctly
- ✅ ADMIN mode logged correctly
- ✅ DESIGNER_ADMIN mode logged correctly
- ✅ All modes distinguishable in logs

---

### Test 5.3: Failed Execution Logging

**Objective:** Verify failed executions logged with error details

```bash
# Test 5.3.1: Execute code that will fail
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{"code": "import os; result = os.getcwd()"}'

# Test 5.3.2: Check audit log for failure
tail -1 data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | jq

# Expected:
# {
#   "success": false,
#   "error": "Module 'os' not allowed in RESTRICTED mode",
#   ...
# }

# Test 5.3.3: Count success vs failures
echo "Success rate:"
TOTAL=$(cat data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | wc -l)
SUCCESS=$(grep '"success":true' data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | wc -l)
echo "Total: $TOTAL, Success: $SUCCESS, Failed: $(($TOTAL - $SUCCESS))"
```

**Pass Criteria:**
- ✅ Failed executions logged with `"success": false`
- ✅ Error messages captured in audit log
- ✅ Success rate calculable from logs
- ✅ No missing log entries

---

## Test Suite 6: Performance & Load Testing

### Test 6.1: Concurrent Execution

**Objective:** Verify process pool handles concurrent requests

```bash
# Test 6.1.1: Sequential execution baseline
time for i in {1..10}; do
  curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "result = 2 ** 20"}' > /dev/null 2>&1
done

# Note the total time (e.g., 5 seconds)

# Test 6.1.2: Concurrent execution (pool size = 3)
time for i in {1..10}; do
  curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "result = 2 ** 20"}' > /dev/null 2>&1 &
done
wait

# Should be faster than sequential (parallelism)

# Test 6.1.3: Pool saturation (more than pool size)
for i in {1..20}; do
  curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "import time; time.sleep(2); result = \"done\""}' &
done

# Monitor pool stats during load
watch -n 1 'curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats | jq'

# Pool should show: available = 0 during load, then recover
```

**Pass Criteria:**
- ✅ Concurrent requests execute in parallel
- ✅ Pool size limits concurrent executions
- ✅ Requests beyond pool size wait (no errors)
- ✅ Pool recovers after load (all processes available)

---

### Test 6.2: Performance Benchmarks

**Objective:** Measure execution performance and establish baselines

```bash
# Test 6.2.1: Simple calculation benchmark
echo "Benchmark: Simple calculation"
for i in {1..100}; do
  curl -s -w "%{time_total}\n" -o /dev/null \
    -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "result = 2 + 2"}'
done | awk '{sum+=$1; count++} END {print "Average:", sum/count*1000, "ms"}'

# Expected: < 100ms average

# Test 6.2.2: Complex calculation benchmark
echo "Benchmark: Complex calculation"
for i in {1..100}; do
  curl -s -w "%{time_total}\n" -o /dev/null \
    -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "import math; result = sum([math.factorial(i) for i in range(10)])"}'
done | awk '{sum+=$1; count++} END {print "Average:", sum/count*1000, "ms"}'

# Expected: < 200ms average

# Test 6.2.3: Module import overhead
echo "Benchmark: Module import"
for i in {1..100}; do
  curl -s -w "%{time_total}\n" -o /dev/null \
    -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "import json, math, datetime; result = None"}'
done | awk '{sum+=$1; count++} END {print "Average:", sum/count*1000, "ms"}'

# Expected: < 150ms average
```

**Performance Targets:**
- Simple calculations: < 100ms average
- Complex calculations: < 200ms average
- Module imports: < 150ms average
- Pool availability: > 95%
- Success rate: > 99%

**Pass Criteria:**
- ✅ All benchmarks meet performance targets
- ✅ Consistent performance across 100 runs
- ✅ No performance degradation over time
- ✅ Baselines documented for future comparison

---

## Test Suite 7: Operational Procedures

### Test 7.1: Backup & Restore

**Objective:** Verify backup and restore procedures work correctly

```bash
# Test 7.1.1: Create test scripts in Designer
# (Manual: Create 3-5 test scripts via Designer IDE)

# Test 7.1.2: Backup scripts
BACKUP_DIR=/tmp/python3-backup
mkdir -p $BACKUP_DIR
tar -czf $BACKUP_DIR/python3-scripts-$(date +%Y-%m-%d).tar.gz \
  -C data python3-scripts/

# Verify backup
tar -tzf $BACKUP_DIR/python3-scripts-$(date +%Y-%m-%d).tar.gz | head -10

# Test 7.1.3: Backup audit logs
tar -czf $BACKUP_DIR/python3-audit-$(date +%Y-%m-%d).tar.gz \
  -C data/python3-integration audit/

# Test 7.1.4: Simulate data loss
rm -rf data/python3-scripts/*

# Test 7.1.5: Restore from backup
tar -xzf $BACKUP_DIR/python3-scripts-$(date +%Y-%m-%d).tar.gz -C data/

# Test 7.1.6: Verify restore
ls -la data/python3-scripts/
# Should show all original scripts restored
```

**Pass Criteria:**
- ✅ Backup creates compressed archives
- ✅ Backup includes all scripts and metadata
- ✅ Restore recovers all files correctly
- ✅ Scripts work after restore (test in Designer)

---

### Test 7.2: Monitoring & Alerting

**Objective:** Verify monitoring scripts detect issues

```bash
# Test 7.2.1: Health monitoring script
cat > /tmp/health-check.sh << 'EOF'
#!/bin/bash
HEALTH=$(curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats | jq -r '.healthy')
TOTAL=$(curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats | jq -r '.totalSize')

if [ "$HEALTH" -lt "$TOTAL" ]; then
  echo "CRITICAL: Unhealthy processes detected ($HEALTH/$TOTAL healthy)"
  exit 2
else
  echo "OK: All processes healthy ($HEALTH/$TOTAL)"
  exit 0
fi
EOF

chmod +x /tmp/health-check.sh
/tmp/health-check.sh

# Expected: "OK: All processes healthy (3/3)"

# Test 7.2.2: Simulate unhealthy process
# (Manual: Kill a Python subprocess to simulate failure)
# pkill -9 -f python_bridge.py (kill one process)

# Wait 30 seconds for health check to detect
sleep 35

# Run monitoring script
/tmp/health-check.sh

# Expected: "CRITICAL: Unhealthy processes detected (2/3)"

# Pool should auto-recover (restart process)
sleep 60
/tmp/health-check.sh

# Expected: "OK: All processes healthy (3/3)"
```

**Pass Criteria:**
- ✅ Monitoring script detects healthy state
- ✅ Monitoring script detects unhealthy state
- ✅ Pool auto-recovers from failures
- ✅ Alerts trigger on unhealthy conditions

---

### Test 7.3: Security Audit

**Objective:** Verify security audit checklist procedures

```bash
# Test 7.3.1: Admin API key strength
grep "python3.admin.apikey" data/ignition.conf | \
  sed 's/.*apikey=//' | wc -c

# Expected: 65+ characters (64 hex chars + newline)

# Test 7.3.2: Audit log analysis
echo "Execution summary:"
cat data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | jq -s '
{
  total: length,
  successful: [.[] | select(.success==true)] | length,
  failed: [.[] | select(.success==false)] | length,
  byMode: group_by(.securityMode) | map({mode: .[0].securityMode, count: length})
}'

# Test 7.3.3: Security mode distribution
echo "Security mode distribution:"
cat data/python3-integration/audit/audit-*.log | \
  jq -r '.securityMode' | sort | uniq -c

# Expected distribution:
# - DESIGNER_ADMIN: Highest (internal users)
# - RESTRICTED: Medium (public API)
# - ADMIN: Lowest (API with key)

# Test 7.3.4: Failed execution review
echo "Recent failed executions:"
grep '"success":false' data/python3-integration/audit/audit-*.log | \
  jq -r '.timestamp + " " + .error' | tail -10
```

**Pass Criteria:**
- ✅ Admin key meets 32+ character requirement
- ✅ Audit logs analyzable with jq
- ✅ Security mode distribution as expected
- ✅ Failed executions reviewable

---

## Test Suite 8: Documentation Verification

### Test 8.1: Documentation Completeness

**Objective:** Verify all Phase 4 documentation exists and is accurate

```bash
# Test 8.1.1: Check documentation files exist
ls -lh python3-integration/docs/*.md

# Expected files:
# - DESIGNER_USER_GUIDE.md (6,500 lines)
# - REST_API_GUIDE.md (3,800 lines)
# - SECURITY_CONFIG_GUIDE.md (4,200 lines)
# - DEPLOYMENT_CHECKLIST.md (514 lines)
# - SECURITY_AUDIT_CHECKLIST.md (581 lines)
# - MONITORING_GUIDE.md (1,200+ lines)
# - BACKUP_RESTORE.md (900+ lines)
# - E2E_TEST_SUITE.md (this file)

# Test 8.1.2: Verify documentation examples work
# Extract code example from REST_API_GUIDE.md and test:
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{"code": "import math; result = math.sqrt(16)"}'

# Should match documented example output

# Test 8.1.3: Verify version references
grep -r "Version.*2.6.0" python3-integration/docs/*.md | wc -l

# Expected: 8+ matches (version header in each doc)

# Test 8.1.4: Check for broken internal links
# (Manual: Review documentation for [file.md](#section) links)
```

**Pass Criteria:**
- ✅ All 8+ documentation files exist
- ✅ Code examples in docs execute correctly
- ✅ Version numbers consistent (2.6.0)
- ✅ No broken links or references

---

### Test 8.2: Documentation Accuracy

**Objective:** Verify documentation matches actual implementation

```bash
# Test 8.2.1: REST API endpoints match documentation
echo "Documented endpoints:"
grep -E "^- `/data/python3integration" python3-integration/docs/REST_API_GUIDE.md

# Compare with actual endpoints (manual Gateway check):
# Navigate to: http://localhost:8088/openapi.json
# Search for "python3integration"

# Test 8.2.2: Security modes match documentation
grep -A 5 "RESTRICTED mode" python3-integration/docs/SECURITY_GUIDE.md

# Verify against actual code behaviour (Test Suite 1 results)

# Test 8.2.3: Configuration examples work
# Extract admin key config example from SECURITY_CONFIG_GUIDE.md
# Test that format matches ignition.conf

grep "wrapper.java.additional" python3-integration/docs/SECURITY_CONFIG_GUIDE.md
grep "wrapper.java.additional" data/ignition.conf

# Formats should match
```

**Pass Criteria:**
- ✅ All documented endpoints exist and work
- ✅ Security mode descriptions accurate
- ✅ Configuration examples valid
- ✅ No discrepancies between docs and implementation

---

## Test Results Summary

### Test Execution Report

**Test Date:** ______________

**Tester Name:** ______________

**Environment:** ☐ Development  ☐ Staging  ☐ Production


---

### Results by Test Suite

| Suite | Test Count | Passed | Failed | Skipped | Pass Rate |
|-------|-----------|--------|--------|---------|-----------|
| 1. Security Modes | 12 | ___ | ___ | ___ | ___% |
| 2. AST Validation | 9 | ___ | ___ | ___ | ___% |
| 3. Designer IDE | 7 | ___ | ___ | ___ | ___% |
| 4. REST API | 9 | ___ | ___ | ___ | ___% |
| 5. Audit Logging | 8 | ___ | ___ | ___ | ___% |
| 6. Performance | 5 | ___ | ___ | ___ | ___% |
| 7. Operations | 9 | ___ | ___ | ___ | ___% |
| 8. Documentation | 6 | ___ | ___ | ___ | ___% |
| **TOTAL** | **65** | **___** | **___** | **___** | **___%** |

---

### Performance Benchmarks

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Simple calculation | < 100ms | ___ ms | ☐ Pass ☐ Fail |
| Complex calculation | < 200ms | ___ ms | ☐ Pass ☐ Fail |
| Module import | < 150ms | ___ ms | ☐ Pass ☐ Fail |
| Concurrent requests (10) | < 3s | ___ s | ☐ Pass ☐ Fail |
| Success rate | > 99% | ___% | ☐ Pass ☐ Fail |

---

### Critical Issues Found

☐ **No critical issues** - All tests passed

☐ **Critical issues found:**
1. ______________________________________________________________
2. ______________________________________________________________
3. ______________________________________________________________

---

### Recommendations

**Security:**
_____________________________________________________________________________________
_____________________________________________________________________________________

**Performance:**
_____________________________________________________________________________________
_____________________________________________________________________________________

**Operations:**
_____________________________________________________________________________________
_____________________________________________________________________________________

---

### Sign-Off

**Overall Assessment:** ☐ PASS - Ready for production  ☐ FAIL - Requires fixes

**Tested By:** ______________

**Date:** ______________

**Reviewed By:** ______________

**Date:** ______________

---

## Automated Test Execution Script

For automated execution of all curl-based tests:

```bash
#!/bin/bash
# e2e-test-runner.sh - Automated E2E test execution

set -e

GATEWAY_URL="http://localhost:8088"
ADMIN_KEY="<your-admin-key>"
PASSED=0
FAILED=0

echo "========================================="
echo "Python 3 Integration E2E Test Suite"
echo "Version: 2.6.0"
echo "========================================="

# Test 1: Health check
echo "Test 1: Health check..."
RESPONSE=$(curl -s $GATEWAY_URL/data/python3integration/api/v1/health)
if echo "$RESPONSE" | jq -e '.status == "healthy"' > /dev/null; then
  echo "✓ PASS"
  ((PASSED++))
else
  echo "✗ FAIL"
  ((FAILED++))
fi

# Test 2: RESTRICTED mode - safe module
echo "Test 2: RESTRICTED mode - safe module..."
RESPONSE=$(curl -s -X POST $GATEWAY_URL/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{"code": "import math; result = math.sqrt(16)"}')
if echo "$RESPONSE" | jq -e '.success == true and .result == 4.0' > /dev/null; then
  echo "✓ PASS"
  ((PASSED++))
else
  echo "✗ FAIL"
  ((FAILED++))
fi

# Test 3: RESTRICTED mode - blocked module
echo "Test 3: RESTRICTED mode - blocked module..."
RESPONSE=$(curl -s -X POST $GATEWAY_URL/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{"code": "import os; result = os.getcwd()"}')
if echo "$RESPONSE" | jq -e '.success == false' > /dev/null; then
  echo "✓ PASS"
  ((PASSED++))
else
  echo "✗ FAIL"
  ((FAILED++))
fi

# Test 4: AST validation - __import__ bypass
echo "Test 4: AST validation - __import__ bypass..."
RESPONSE=$(curl -s -X POST $GATEWAY_URL/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{"code": "__import__(\"os\").system(\"ls\")"}')
if echo "$RESPONSE" | jq -e '.success == false' > /dev/null; then
  echo "✓ PASS"
  ((PASSED++))
else
  echo "✗ FAIL"
  ((FAILED++))
fi

# Test 5: DESIGNER_ADMIN mode
echo "Test 5: DESIGNER_ADMIN mode..."
RESPONSE=$(curl -s -X POST $GATEWAY_URL/data/python3integration/api/v1/exec \
  -H "User-Agent: Ignition-Designer/8.3" \
  -H "Content-Type: application/json" \
  -d '{"code": "import os; result = os.getcwd()"}')
if echo "$RESPONSE" | jq -e '.success == true' > /dev/null; then
  echo "✓ PASS"
  ((PASSED++))
else
  echo "✗ FAIL"
  ((FAILED++))
fi

# Test 6: Pool stats
echo "Test 6: Pool stats..."
RESPONSE=$(curl -s $GATEWAY_URL/data/python3integration/api/v1/pool-stats)
if echo "$RESPONSE" | jq -e '.totalSize > 0 and .healthy > 0' > /dev/null; then
  echo "✓ PASS"
  ((PASSED++))
else
  echo "✗ FAIL"
  ((FAILED++))
fi

echo "========================================="
echo "Test Results: $PASSED passed, $FAILED failed"
echo "Success Rate: $(( $PASSED * 100 / ($PASSED + $FAILED) ))%"
echo "========================================="

if [ $FAILED -eq 0 ]; then
  echo "✓ ALL TESTS PASSED"
  exit 0
else
  echo "✗ SOME TESTS FAILED"
  exit 1
fi
```

Save as `e2e-test-runner.sh`, make executable (`chmod +x`), and run:

```bash
./e2e-test-runner.sh
```

---

*This test suite was created for Python 3 Integration v2.6.0 - Last updated October 2025*
