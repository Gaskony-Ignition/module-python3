# Python 3 Integration - REST API Guide

**Audience:** API Developers, System Integrators

> **Architecture Note (v3.7.0+):** REST endpoint handlers were refactored from a single
> God class into companion classes (ExecutionHandlers, ScriptAndPackageHandlers, MonitoringHandlers).
> The API itself is unchanged — all routes and responses are identical.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Authentication](#authentication)
3. [API Endpoints](#api-endpoints)
4. [Security Modes](#security-modes)
5. [Request/Response Formats](#requestresponse-formats)
6. [Code Examples](#code-examples)
7. [Error Handling](#error-handling)
8. [Rate Limiting](#rate-limiting)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)

---

## Introduction

The Python 3 Integration module provides a RESTful API for executing Python 3 code on the Ignition Gateway. This allows external systems to leverage Python 3 capabilities via HTTP requests.

### Base URL

All API endpoints follow the pattern:
```
http(s)://<gateway-host>:<port>/data/python3integration/api/v1/<endpoint>
```

**Default Gateway Port:** 8088

**Example:**
```
http://localhost:8088/data/python3integration/api/v1/exec
```

### API Versioning

Current API version: **v1**

The API is versioned to ensure backward compatibility. All endpoints include `/api/v1/` in the path.

---

## Authentication

The module requires an authenticated caller for every REST execution route —
see the trust model in [SECURITY.md](../../SECURITY.md).

### 1. Unauthenticated Requests → 403 Forbidden

The REST API does not accept anonymous calls. Authenticate via the admin key
path or via an Ignition session.

```bash
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{"code": "import math; result = math.sqrt(16)"}'
# → 403 Forbidden
```

If your existing automation relied on the old RESTRICTED tier, mint an admin key (below) and add `Authorization: Bearer …`.

---

### 2. ADMIN Mode (Admin API Key Required)

**Who:** Authenticated API users with admin key
**Access:** Extended capabilities (os, sys, subprocess, requests, etc.)
**Use Case:** Trusted systems, automation scripts

#### Configuring Admin API Key

**Step 1: Generate Secure Key**
```bash
# Generate 32+ character key (required minimum)
openssl rand -hex 32
```

**Example output:**
```
a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2
```

**Step 2: Configure in Ignition**

Edit `<ignition>/data/ignition.conf`:
```properties
wrapper.java.additional.200=-Dignition.python3.admin.apikey=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2
```

**Step 3: Restart Gateway**
```bash
./gwcmd.sh -r
```

#### Using Admin API Key

**Method 1: Bearer Token (Recommended)**
```bash
curl -X POST https://gateway:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer a1b2c3d4e5f6...e1f2" \
  -d '{"code": "import os; result = os.getcwd()"}'
```

**Method 2: Custom Header (Legacy)**
```bash
curl -X POST https://gateway:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -H "X-Python3-Admin-Key: a1b2c3d4e5f6...e1f2" \
  -d '{"code": "import os; result = os.getcwd()"}'
```

#### HTTPS Requirement

**ADMIN mode requires HTTPS by default** for security:

```bash
# ✅ HTTPS - Works
curl -X POST https://gateway:8088/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer <key>" \
  ...

# ❌ HTTP - Fails with error
curl -X POST http://gateway:8088/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer <key>" \
  ...
```

**Error:**
```json
{
  "success": false,
  "error": "ADMIN mode requires HTTPS. Use 'https://' or disable with -Dignition.python3.admin.requirehttps=false (NOT recommended for production)"
}
```

**Disable HTTPS requirement (development only):**
```properties
# In ignition.conf
wrapper.java.additional.201=-Dignition.python3.admin.requirehttps=false
```

---

### 3. DESIGNER_ADMIN Mode (Designer IDE Only)

**Who:** Ignition Designer IDE users
**Access:** Full Python capabilities
**Use Case:** Designer Python 3 IDE, Gateway Web UI browser session

As of v4.2.0, the Designer's core script authoring and exec/eval path talks
to the Gateway over the platform's **authenticated module-RPC channel**, not
this REST API — see `SECURITY.md`. A handful of secondary Designer panels
(packages/distributions/completions/shell/diagnostics) not yet migrated,
plus the browser-based Gateway Web UI, still use this REST API's
`POST /auth/session` endpoint to mint a short-lived bearer token. That
endpoint (fixed under review item C14) binds the issued mode to the
caller's **actual Ignition role membership** — `Administrator` or
`Designer` role → `DESIGNER_ADMIN`; anyone else → `403 Forbidden`. The
previous behaviour (any caller claiming a `client_id` starting with
`ignition-designer-` received a privileged token) was a vulnerability and
was removed.

---

## API Endpoints

> **All endpoints below require authentication** (see
> [Authentication](#authentication)) — the curl examples in this section
> omit the `Authorization` header for brevity; add
> `-H "Authorization: Bearer $API_KEY"` (or an authenticated Designer/Web UI
> session) to every request or expect a 401/403.

### POST /api/v1/exec

Execute Python statements (doesn't return value).

**Request:**
```json
{
  "code": "print('Hello'); x = 2 + 2",
  "variables": {
    "input_value": 10
  }
}
```

**Response:**
```json
{
  "success": true,
  "result": "Hello\n",
  "executionTimeMs": 125,
  "timestamp": 1698765432000
}
```

**Example:**
```bash
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{
    "code": "import math; print(math.sqrt(16))",
    "variables": {}
  }'
```

---

### POST /api/v1/eval

Evaluate Python expression (returns value).

**Request:**
```json
{
  "expression": "x + y",
  "variables": {
    "x": 10,
    "y": 20
  }
}
```

**Response:**
```json
{
  "success": true,
  "result": "30",
  "executionTimeMs": 50,
  "timestamp": 1698765432000
}
```

**Example:**
```bash
curl -X POST http://localhost:8088/data/python3integration/api/v1/eval \
  -H "Content-Type: application/json" \
  -d '{
    "expression": "2 ** 10",
    "variables": {}
  }'
```

---

### POST /api/v1/call-module

Call a function from a Python module.

**Request:**
```json
{
  "module": "math",
  "function": "sqrt",
  "args": [16]
}
```

**Response:**
```json
{
  "success": true,
  "result": "4.0",
  "executionTimeMs": 25,
  "timestamp": 1698765432000
}
```

**Example:**
```bash
curl -X POST http://localhost:8088/data/python3integration/api/v1/call-module \
  -H "Content-Type: application/json" \
  -d '{
    "module": "json",
    "function": "dumps",
    "args": [{"key": "value"}]
  }'
```

---

### GET /api/v1/version

Get Python version information.

**Response:**
```json
{
  "pythonVersion": "3.11.2",
  "platform": "Linux-5.15.0-x86_64",
  "implementation": "CPython"
}
```

**Example:**
```bash
curl http://localhost:8088/data/python3integration/api/v1/version
```

---

### GET /api/v1/pool-stats

Get Python process pool statistics.

**Response:**
```json
{
  "totalSize": 3,
  "healthy": 3,
  "available": 2,
  "inUse": 1
}
```

**Example:**
```bash
curl http://localhost:8088/data/python3integration/api/v1/pool-stats
```

---

### GET /api/v1/health

Health check endpoint.

**Response:**
```json
{
  "healthy": true,
  "pythonVersion": "3.11.2",
  "poolSize": 3
}
```

**Example:**
```bash
curl http://localhost:8088/data/python3integration/api/v1/health
```

---

### GET /api/v1/diagnostics

Comprehensive diagnostics information.

**Response:**
```json
{
  "pythonVersion": "3.11.2",
  "platform": "Linux",
  "poolStats": {
    "totalSize": 3,
    "healthy": 3,
    "available": 3,
    "inUse": 0
  },
  "memoryUsageMb": 128.5,
  "cpuUsagePercent": 2.3,
  "uptime": 3600000
}
```

**Example:**
```bash
curl http://localhost:8088/data/python3integration/api/v1/diagnostics
```

---

## Security Modes (v4.0.0+)

### Mode Comparison

| Feature | Unauthenticated | ADMIN | DESIGNER_ADMIN |
|---------|----------------|-------|----------------|
| Authentication | n/a — rejected | API Key + HTTPS | Ignition session (Designer/Admin role) |
| Result | 403 Forbidden | Full Python capability | Full Python capability |
| File I/O / network / subprocess | n/a | ✅ Yes | ✅ Yes |
| Use Case | n/a | Trusted automation | Designer IDE / interactive |

Both authenticated modes grant the same capability set — they are distinguished only for audit-log clarity (browser/Designer login vs. headless API-key caller).

### Available modules

All standard-library Python 3 modules are available to authenticated callers, including `os`, `sys`, `subprocess`, `socket`, `urllib`, `requests`, `pandas`, `numpy`, `ctypes`, `multiprocessing`, etc. There is no module whitelist or deny-list in v4.0.0+ — `python_bridge.py` performs no source-level filtering at all (it even uses `ctypes` itself, on Windows, to apply process resource limits).

For real isolation between callers and the Gateway host, deploy the Gateway in a container/VM whose blast radius matches your trust requirements. The real boundary is OS-level host isolation plus who holds the Designer/Administrator role — see `docs/PROJECT_CHARTER.md` §2.

### What changed in v4.0.0

- The `RESTRICTED` tier for unauthenticated callers was removed. AST-based whitelist validation went with it.
- The previous "safe modules" tier is no longer accessible to anonymous clients. If you relied on it, see the migration notes at the top of this document.

---

## Request/Response Formats

### Request Format

All POST endpoints accept JSON:

```json
{
  "code": "string (required for /exec)",
  "expression": "string (required for /eval)",
  "module": "string (required for /call-module)",
  "function": "string (required for /call-module)",
  "args": "array (optional for /call-module)",
  "variables": "object (optional for /exec, /eval)"
}
```

### Response Format

#### Success Response

```json
{
  "success": true,
  "result": "string|number|object",
  "executionTimeMs": 125,
  "timestamp": 1698765432000
}
```

#### Error Response

```json
{
  "success": false,
  "error": "Error message",
  "traceback": "Python stack trace (if available)",
  "timestamp": 1698765432000
}
```

### HTTP Status Codes

| Code | Meaning | Description |
|------|---------|-------------|
| 200 | Success | Request completed successfully |
| 400 | Bad Request | Invalid JSON or missing required fields |
| 401 | Unauthorized | Invalid API key |
| 403 | Forbidden | Security violation (HTTPS required, etc.) |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected server error |

---

## Code Examples

### Python

```python
import requests
import json

# Configuration
GATEWAY_URL = "http://localhost:8088"
API_KEY = "a1b2c3d4e5f6..."  # Your admin key

# Unauthenticated request — returns 403 in v4.0.0+
def execute_safe_code():
    url = f"{GATEWAY_URL}/data/python3integration/api/v1/exec"
    payload = {
        "code": "import math; result = math.sqrt(16)",
        "variables": {}
    }

    response = requests.post(url, json=payload)
    data = response.json()

    if data['success']:
        print(f"Result: {data['result']}")
    else:
        print(f"Error: {data['error']}")

# ADMIN mode (with auth)
def execute_admin_code():
    url = f"https://localhost:8088/data/python3integration/api/v1/exec"
    headers = {
        "Authorization": f"Bearer {API_KEY}"
    }
    payload = {
        "code": "import os; result = os.getcwd()",
        "variables": {}
    }

    response = requests.post(url, json=payload, headers=headers, verify=False)
    data = response.json()

    if data['success']:
        print(f"Current directory: {data['result']}")
    else:
        print(f"Error: {data['error']}")

# Call module function
def call_module_function():
    url = f"{GATEWAY_URL}/data/python3integration/api/v1/call-module"
    payload = {
        "module": "json",
        "function": "dumps",
        "args": [{"name": "Alice", "age": 25}]
    }

    response = requests.post(url, json=payload)
    data = response.json()

    if data['success']:
        print(f"JSON: {data['result']}")
    else:
        print(f"Error: {data['error']}")

if __name__ == "__main__":
    execute_safe_code()
    execute_admin_code()
    call_module_function()
```

---

### JavaScript (Node.js)

```javascript
const axios = require('axios');

const GATEWAY_URL = 'http://localhost:8088';
const API_KEY = 'a1b2c3d4e5f6...';

// Unauthenticated request — returns 403 in v4.0.0+
async function executeSafeCode() {
  try {
    const response = await axios.post(
      `${GATEWAY_URL}/data/python3integration/api/v1/exec`,
      {
        code: 'import math; result = math.sqrt(16)',
        variables: {}
      }
    );

    console.log('Result:', response.data.result);
  } catch (error) {
    console.error('Error:', error.response.data);
  }
}

// ADMIN mode
async function executeAdminCode() {
  try {
    const response = await axios.post(
      `https://localhost:8088/data/python3integration/api/v1/exec`,
      {
        code: 'import os; result = os.getcwd()',
        variables: {}
      },
      {
        headers: {
          'Authorization': `Bearer ${API_KEY}`
        },
        httpsAgent: new https.Agent({ rejectUnauthorized: false })
      }
    );

    console.log('Current directory:', response.data.result);
  } catch (error) {
    console.error('Error:', error.response.data);
  }
}

// Run examples
executeSafeCode();
executeAdminCode();
```

---

### cURL

```bash
#!/bin/bash

GATEWAY="http://localhost:8088"
API_KEY="a1b2c3d4e5f6..."

# Unauthenticated request — returns 403 in v4.0.0+ (kept for v3.x reference)
curl -X POST "$GATEWAY/data/python3integration/api/v1/exec" \
  -H "Content-Type: application/json" \
  -d '{"code": "import math; result = math.sqrt(16)"}'

# ADMIN mode - Execute with admin key
curl -X POST "https://localhost:8088/data/python3integration/api/v1/exec" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{"code": "import os; result = os.getcwd()"}'

# Evaluate expression
curl -X POST "$GATEWAY/data/python3integration/api/v1/eval" \
  -H "Content-Type: application/json" \
  -d '{
    "expression": "x * y",
    "variables": {"x": 10, "y": 5}
  }'

# Call module function
curl -X POST "$GATEWAY/data/python3integration/api/v1/call-module" \
  -H "Content-Type: application/json" \
  -d '{
    "module": "math",
    "function": "factorial",
    "args": [5]
  }'

# Health check
curl "$GATEWAY/data/python3integration/api/v1/health"

# Pool stats
curl "$GATEWAY/data/python3integration/api/v1/pool-stats"
```

---

### Java

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Python3APIClient {
    private static final String GATEWAY_URL = "http://localhost:8088";
    private static final String API_KEY = "a1b2c3d4e5f6...";

    private final HttpClient httpClient;

    public Python3APIClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    // Unauthenticated request — returns 403 in v4.0.0+
    public void executeSafeCode() throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("code", "import math; result = math.sqrt(16)");
        request.add("variables", new JsonObject());

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(GATEWAY_URL + "/data/python3integration/api/v1/exec"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
            .build();

        HttpResponse<String> response = httpClient.send(
            httpRequest,
            HttpResponse.BodyHandlers.ofString()
        );

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        if (result.get("success").getAsBoolean()) {
            System.out.println("Result: " + result.get("result").getAsString());
        } else {
            System.err.println("Error: " + result.get("error").getAsString());
        }
    }

    // ADMIN mode
    public void executeAdminCode() throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("code", "import os; result = os.getcwd()");
        request.add("variables", new JsonObject());

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://localhost:8088/data/python3integration/api/v1/exec"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + API_KEY)
            .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
            .build();

        HttpResponse<String> response = httpClient.send(
            httpRequest,
            HttpResponse.BodyHandlers.ofString()
        );

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        if (result.get("success").getAsBoolean()) {
            System.out.println("Current directory: " + result.get("result").getAsString());
        } else {
            System.err.println("Error: " + result.get("error").getAsString());
        }
    }

    public static void main(String[] args) {
        Python3APIClient client = new Python3APIClient();
        try {
            client.executeSafeCode();
            client.executeAdminCode();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

## Error Handling

### Common Errors

#### 1. Authentication Required

```json
{
  "success": false,
  "error": "403 Forbidden — authentication required (the previous 'RESTRICTED mode' was removed in v4.0.0)"
}
```

**Solution:** Use ADMIN mode with API key

---

#### 2. Syntax Error

```json
{
  "success": false,
  "error": "SyntaxError: invalid syntax",
  "traceback": "  File \"<string>\", line 1\n    import os print('test')\n              ^\nSyntaxError: invalid syntax"
}
```

**Solution:** Fix Python syntax

---

#### 3. HTTPS Required

```json
{
  "success": false,
  "error": "ADMIN mode requires HTTPS. Use 'https://' or disable with -Dignition.python3.admin.requirehttps=false"
}
```

**Solution:** Use HTTPS or disable requirement (dev only)

---

#### 4. Invalid API Key

```json
{
  "success": false,
  "error": "Invalid API key"
}
```

**Solution:** Check API key configuration

---

#### 5. Rate Limit Exceeded

```json
{
  "success": false,
  "error": "Rate limit exceeded. Maximum 100 requests per minute."
}
```

**Solution:** Slow down requests or increase rate limit

---

## Rate Limiting

The API enforces rate limits to prevent abuse:

- **Limit:** 100 requests per minute per IP address
- **Scope:** Per IP, not per API key
- **Response:** HTTP 429 Too Many Requests

**Example:**
```bash
# After 100 requests in 1 minute
curl http://localhost:8088/data/python3integration/api/v1/exec ...

# Response:
{
  "success": false,
  "error": "Rate limit exceeded. Maximum 100 requests per minute."
}
```

**Checking Remaining Requests:**
The API does not currently expose rate limit headers, but you can track your requests client-side.

---

## Best Practices

### 1. Use HTTPS in Production

Always use HTTPS for ADMIN mode:
```bash
# ✅ Good
curl -X POST https://gateway:8088/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer $API_KEY" \
  ...

# ❌ Bad
curl -X POST http://gateway:8088/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer $API_KEY" \
  ...
```

### 2. Store API Keys Securely

Never hardcode API keys:
```python
# ❌ Bad
API_KEY = "a1b2c3d4e5f6..."

# ✅ Good
import os
API_KEY = os.environ.get('PYTHON3_API_KEY')
```

### 3. Handle Errors Gracefully

```python
try:
    response = requests.post(url, json=payload)
    response.raise_for_status()  # Raise exception for 4xx/5xx
    data = response.json()

    if not data['success']:
        logging.error(f"Python execution failed: {data['error']}")
        return None

    return data['result']
except requests.RequestException as e:
    logging.error(f"HTTP request failed: {e}")
    return None
```

### 4. Use Connection Pooling

```python
import requests

# Create session for connection pooling
session = requests.Session()

# Make multiple requests
for i in range(10):
    response = session.post(url, json=payload)
    data = response.json()
    print(data['result'])
```

### 5. Validate Input

```python
def execute_python(code):
    # Validate code size
    if len(code) > 1_000_000:  # 1MB limit
        raise ValueError("Code too large")

    # Validate code is not empty
    if not code.strip():
        raise ValueError("Code cannot be empty")

    # Execute
    response = requests.post(url, json={"code": code})
    return response.json()
```

### 6. Monitor and Log

```python
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def execute_with_logging(code):
    logger.info(f"Executing code (length: {len(code)})")

    start = time.time()
    response = requests.post(url, json={"code": code})
    duration = time.time() - start

    logger.info(f"Execution completed in {duration:.2f}s")

    data = response.json()
    if not data['success']:
        logger.error(f"Execution failed: {data['error']}")

    return data
```

---

## Troubleshooting

### Connection Refused

**Problem:** `Connection refused` or `Failed to connect`

**Solutions:**
1. Verify Gateway is running: `http://localhost:8088/StatusPing`
2. Check firewall rules
3. Verify port 8088 is correct
4. Try `https://` instead of `http://`

### 401 Unauthorized

**Problem:** API key rejected

**Solutions:**
1. Verify API key is correct (32+ characters)
2. Check `ignition.conf` configuration
3. Restart Gateway after configuring key
4. Use `Authorization: Bearer <key>` header format

### 403 Forbidden (HTTPS Required)

**Problem:** HTTP used with ADMIN mode

**Solutions:**
1. Use `https://` instead of `http://`
2. Or disable HTTPS requirement (dev only):
   ```
   -Dignition.python3.admin.requirehttps=false
   ```

### Module Not Found

**Problem:** `ModuleNotFoundError: No module named 'pandas'`

**Solutions:**
1. Install module via API:
   ```bash
   curl -X POST https://gateway:8088/data/python3integration/api/v1/exec \
     -H "Authorization: Bearer $API_KEY" \
     -d '{"code": "import subprocess; subprocess.run([\"pip\", \"install\", \"pandas\"])"}'
   ```

2. Or install via Gateway script console

### Timeout

**Problem:** Request times out after 30 seconds

**Solutions:**
1. Optimize Python code
2. Check process pool availability (`/api/v1/pool-stats`)
3. Increase pool size in `ignition.conf`:
   ```
   -Dignition.python3.poolsize=10
   ```

---

## API Reference Summary

All endpoints require authentication (Administrator/Designer session token
or admin API key) — there is no unauthenticated tier as of v4.0.0, including
the read-only GET endpoints below:

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/v1/exec` | POST | Required | Execute Python statements |
| `/api/v1/eval` | POST | Required | Evaluate Python expression |
| `/api/v1/call-module` | POST | Required | Call module function |
| `/api/v1/version` | GET | Required | Get Python version |
| `/api/v1/pool-stats` | GET | Required | Get pool statistics |
| `/api/v1/health` | GET | Required | Health check |
| `/api/v1/diagnostics` | GET | Required | Detailed diagnostics |

---

## Additional Resources

- **Security Guide**: See `SECURITY_GUIDE.md` for security details
- **User Guide**: See `DESIGNER_USER_GUIDE.md` for Designer IDE usage
- **Python Documentation**: https://docs.python.org/3/
- **Requests Library**: https://requests.readthedocs.io/

---

**Questions?** Open an issue on GitHub or post in the Ignition Forum.

*This guide was created for Python 3 Integration v2.6.0 - Last updated October 2025*
