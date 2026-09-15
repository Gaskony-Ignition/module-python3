# Quick Start Guide

**Module:** Python 3 Integration for Ignition 8.3+

Get started with the Python 3 Integration module in under 30 minutes.

---

## Prerequisites

Before you begin, ensure you have:

### Required Software
- **Java 17+** (JDK) - Required for building the module
- **Python 3.9, 3.11, or 3.12** - Runtime for Python execution
- **Ignition 8.3+** - Ignition Gateway installation
- **Git** - For cloning the repository
- **Gradle 8.x** - Build tool (included via wrapper)

### System Requirements
- **OS:** Windows, Linux, or macOS
- **RAM:** 4GB minimum, 8GB recommended
- **Disk Space:** 500MB for build artifacts

### Verify Prerequisites

```bash
# Check Java version (must be 17+)
java -version

# Check Python version (must be 3.9, 3.11, or 3.12)
python3 --version

# Check Git
git --version
```

---

## 5-Minute Quick Start

### 1. Clone Repository

```bash
git clone https://github.com/Gaskony-Ignition/ignition-module-python3.git
cd ignition-module-python3/python3-integration
```

### 2. Build Module

```bash
./gradlew clean build --no-daemon
```

**Expected output:**
```
BUILD SUCCESSFUL in 30s
184 tests passing
Module: build/libs/python3-integration-signed.modl
```

**Build time:** ~30-35 seconds on modern hardware

**Artifacts created:**
- `build/libs/python3-integration-signed.modl` - Signed module file
- `build/reports/tests/test/index.html` - Test report

### 3. Install in Ignition Gateway

#### Step 1: Access Gateway Web Interface
1. Open browser to `http://localhost:8088` (or your Gateway URL)
2. Login with admin credentials

#### Step 2: Navigate to Modules
1. Click **Config** (top menu)
2. Select **System** → **Modules** (left sidebar)

#### Step 3: Install Module
1. Scroll to bottom: **Install or Upgrade a Module**
2. Click **Choose File**
3. Navigate to: `build/Python3-*.modl`
4. Click **Install**

#### Step 4: Wait for Installation
- Module status: "Loading..." → "Running" (~10 seconds)
- Gateway logs: Check wrapper.log for "Python3" entries

**Expected log output:**
```
INFO  [Python3ProcessPool] Initializing Python 3 process pool (size: 3)
INFO  [Python3ProcessPool] Python path: /usr/bin/python3
INFO  [GatewayHook] Python 3 Integration module started successfully
```

### 4. Test Installation

You have two options to test the module:

#### Option A: Script Console (Quick Test)

1. Open **Designer**
2. Navigate to **Tools** → **Script Console**
3. Test with these commands:

```python
# Get Python version
print system.python3.getVersion()
# Output: {'version': '3.11.5', 'executable': '/usr/bin/python3'}

# Execute simple code
result = system.python3.exec("result = 2 + 2")
print result  # Output: 4

# Evaluate expression
result = system.python3.eval("10 * 5")
print result  # Output: 50

# Check pool statistics
stats = system.python3.getPoolStats()
print stats
# Output: {'poolSize': 3, 'availableExecutors': 3, 'totalExecutions': 2}
```

#### Option B: Python 3 IDE (Full Experience)

1. Open **Designer**
2. Navigate to **Tools** → **Python 3 IDE**
3. In the IDE window:
   - **Gateway URL:** Enter `http://localhost:8088` (or your Gateway URL)
   - Click **Connect**
4. Write Python 3 code in the editor:

```python
print("Hello from Python 3!")
import sys
print(f"Python version: {sys.version}")
```

5. Click **Execute** (or press Ctrl+Enter)
6. View output in the **Output** tab

**Expected output:**
```
Hello from Python 3!
Python version: 3.11.5 (main, Aug 24 2023, 15:18:16) [GCC 11.3.0]
```

---

## Troubleshooting

### Build Fails

**Problem:** `./gradlew build` fails

**Solutions:**
```bash
# Check Java version (must be 17+)
java -version

# Check Gradle wrapper
./gradlew --version

# Clean build with dependency refresh
./gradlew clean build --no-daemon --refresh-dependencies

# Check for file permission issues
chmod +x gradlew
```

### Module Won't Load

**Problem:** Module shows "Failed" status in Gateway

**Solutions:**

1. **Check Gateway logs:**
   ```bash
   tail -f <ignition-install>/logs/wrapper.log | grep -i python3
   ```

2. **Look for common errors:**
   - "Python executable not found" → Install Python 3 or configure path
   - "Permission denied" → Check Python executable permissions
   - "Module signature invalid" → Use signed .modl file from build/libs/

3. **Verify Python installation:**
   ```bash
   which python3
   python3 --version
   ```

4. **Configure Python path (if auto-detection fails):**

   Edit `<ignition-install>/data/ignition.conf`:
   ```properties
   wrapper.java.additional.101=-Dignition.python3.path=/usr/bin/python3
   ```

   Restart Gateway after editing.

### Tests Fail

**Problem:** Build fails with test errors

**Solutions:**
```bash
# Run specific test class
./gradlew test --tests "Python3ExecutorTest"

# Run with verbose output
./gradlew test --info

# View test report
open build/reports/tests/test/index.html
# Or on Linux: xdg-open build/reports/tests/test/index.html

# Skip tests (not recommended)
./gradlew build -x test
```

### IDE Won't Connect to Gateway

**Problem:** "Connection failed" in Python 3 IDE

**Solutions:**

1. **Verify Gateway URL:**
   - Correct format: `http://localhost:8088` (include http://)
   - Check Gateway is running: Browse to URL in web browser

2. **Check network connectivity:**
   ```bash
   curl http://localhost:8088/data/python3integration/api/v1/health
   # Expected: {"status":"healthy","poolSize":3}
   ```

3. **Check module status:**
   - Gateway → Config → System → Modules
   - Python 3 Integration should show "Running"

4. **Check Designer logs:**
   - Designer Console output shows connection errors

### Python Import Errors

**Problem:** "ModuleNotFoundError" when executing code

**Solutions:**

1. **Check Python environment:**
   ```bash
   python3 -m pip list
   ```

2. **Install missing packages:**
   ```bash
   python3 -m pip install <package-name>
   ```

3. **Use virtual environment (recommended):**
   ```bash
   python3 -m venv /path/to/venv
   source /path/to/venv/bin/activate  # Linux/Mac
   # Or: \path\to\venv\Scripts\activate  # Windows
   pip install <package-name>
   ```

   Then configure Ignition to use venv Python:
   ```properties
   wrapper.java.additional.101=-Dignition.python3.path=/path/to/venv/bin/python3
   ```

---

## Next Steps

### Author a script and call it from your project

Ready to write a real Python 3 script and call it from a Perspective
button, a tag change script, or a gateway event? See the
**[Integration Guide](INTEGRATION_GUIDE.md)** — it walks through authoring
in the Project Browser, testing in the Script Console, calling
`system.python3.callScript`/`exec`/`eval`/`callModule` from project Jython,
and the security rules (runtime scripting default, injection anti-pattern)
that apply.

### Learn the Architecture
- **Architecture Overview:** [V2_ARCHITECTURE_GUIDE.md](../V2_ARCHITECTURE_GUIDE.md)
- **Component Details:** Gateway scope (process pool) + Designer scope (IDE)
- **Data Flow:** How Python code is executed via REST API

### Explore REST API
The module also exposes a REST API for remote/external execution (not used
by project Jython scripts — see the Integration Guide for that path). As of
v4.0.0, **every** REST endpoint requires authentication (Administrator/
Designer session token or admin API key) — there is no unauthenticated
tier. See [REST_API.md](../api/REST_API.md) for the full authentication
flow.

**Base URL:** `http://localhost:8088/data/python3integration/api/v1/`

**Key Endpoints:**
- `POST /exec` - Execute Python statements
- `POST /eval` - Evaluate Python expressions
- `GET /version` - Python version info
- `GET /pool-stats` - Process pool statistics
- `GET /health` - Health check

**Example (with an admin API key — required):**
```bash
curl -X POST https://localhost:8088/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer <api-key>" \
  -H "Content-Type: application/json" \
  -d '{"code": "result = 2 + 2", "variables": {}}'
```

### Use in Scripts
Access Python 3 from Ignition scripts:

```python
# In any Ignition script (Vision, Perspective, Gateway)
result = system.python3.exec("import math; result = math.sqrt(16)")
print result  # 4.0

# Pass variables to Python
variables = {"x": 10, "y": 20}
result = system.python3.eval("x + y", variables)
print result  # 30

# Call Python module functions
result = system.python3.callModule("math", "factorial", [5])
print result  # 120
```

### Advanced Features

#### 1. Script Management
- Save scripts with names and metadata
- Organize scripts in folders
- Import/Export scripts to .py files
- Find/Replace across scripts

#### 2. Theme Customization
- Dark theme (default)
- Light theme
- VS Code Dark+ theme
- Custom themes via RSyntaxTextArea

#### 3. Keyboard Shortcuts
- **Ctrl+Enter** - Execute code
- **Ctrl+S** - Save script
- **Ctrl+F** - Find text
- **Ctrl+H** - Replace text
- **Ctrl+Shift+P** - Command palette
- **Ctrl+B** - Toggle sidebar

#### 4. Performance Monitoring
- Real-time execution metrics
- Process pool utilization
- Python version information
- Health indicators

### Contribute
Want to contribute to the module?

1. **Fork the repository** on GitHub
2. **Read CONTRIBUTING.md** (if exists) or check README.md
3. **Run tests:** `./gradlew test`
4. **Submit pull request** with clear description

**Development workflow:**
```bash
# Make changes to code
vi src/main/java/.../MyFile.java

# Run tests
./gradlew test

# Build module
./gradlew clean build

# Test in local Gateway
# Install .modl file from build/libs/

# Commit changes
git add .
git commit -m "Description of changes"
git push
```

---

## Common Use Cases

### Use Case 1: Data Processing
Execute Python data processing from Ignition scripts:

```python
# Ignition script
code = """
import pandas as pd
data = pd.DataFrame({'A': [1, 2, 3], 'B': [4, 5, 6]})
result = data.sum().to_dict()
"""
result = system.python3.exec(code)
print result  # {'A': 6, 'B': 15}
```

### Use Case 2: Machine Learning Inference
Call Python ML models from Ignition:

```python
# Train model in Python (offline)
# joblib.dump(model, 'model.pkl')

# Use model in Ignition
code = """
import joblib
model = joblib.load('/path/to/model.pkl')
result = model.predict([[feature1, feature2, feature3]])
"""
result = system.python3.exec(code, {"feature1": 1.5, "feature2": 2.3, "feature3": 0.8})
```

### Use Case 3: External API Integration
Access external APIs using Python requests:

```python
code = """
import requests
response = requests.get('https://api.example.com/data')
result = response.json()
"""
result = system.python3.exec(code)
```

### Use Case 4: File Processing
Process files on Gateway server:

```python
code = """
import csv
with open('/path/to/file.csv', 'r') as f:
    reader = csv.DictReader(f)
    result = list(reader)
"""
result = system.python3.exec(code)
```

---

## Configuration

### Python Path Configuration

The module auto-detects Python 3 in this order:

1. **System property:** `-Dignition.python3.path=/path/to/python3`
2. **Environment variable:** `IGNITION_PYTHON3_PATH`
3. **Auto-detection:** OS-specific common paths
4. **Fallback:** `python3` command

**To configure manually:**

Edit `<ignition-install>/data/ignition.conf`:
```properties
# Add after existing wrapper.java.additional.* lines
wrapper.java.additional.101=-Dignition.python3.path=/usr/bin/python3.11
```

### Pool Size Configuration

Default pool size: **3 processes**

To change pool size, add to `ignition.conf`:
```properties
wrapper.java.additional.102=-Dignition.python3.poolsize=5
```

**Recommended pool sizes:**
- **Light usage (< 10 scripts/minute):** 3 processes
- **Medium usage (10-50 scripts/minute):** 5 processes
- **Heavy usage (50+ scripts/minute):** 10 processes

**Note:** More processes = more memory usage (~50MB per process)

### Timeout Configuration

Default execution timeout: **30 seconds**

To change timeout:
```properties
wrapper.java.additional.103=-Dignition.python3.timeout=60
```

---

## Support and Resources

### Documentation
- **Architecture Guide:** [../V2_ARCHITECTURE_GUIDE.md](../V2_ARCHITECTURE_GUIDE.md)
- **Testing Guide:** [../TESTING_GUIDE.md](../TESTING_GUIDE.md)
- **Version Workflow:** [../VERSION_UPDATE_WORKFLOW.md](../VERSION_UPDATE_WORKFLOW.md)
- **Feature Comparison:** [../V2_FEATURE_COMPARISON_AND_ROADMAP.md](../V2_FEATURE_COMPARISON_AND_ROADMAP.md)

### External Resources
- **Ignition SDK Docs:** https://www.sdk-docs.inductiveautomation.com/
- **SDK Examples:** https://github.com/inductiveautomation/ignition-sdk-examples
- **Ignition Forum:** https://forum.inductiveautomation.com/

### Getting Help
1. **Check documentation** in `docs/` directory
2. **Review troubleshooting** section above
3. **Check Gateway logs** in `<ignition-install>/logs/wrapper.log`
4. **Search Ignition Forum** for similar issues
5. **Open GitHub issue** with detailed error information

---

## Success Checklist

After completing this guide, you should be able to:

- [ ] Build the module from source
- [ ] Install module in Ignition Gateway
- [ ] Execute Python 3 code via Script Console
- [ ] Use Python 3 IDE in Designer
- [ ] Save and load scripts
- [ ] Check process pool statistics
- [ ] Access REST API endpoints
- [ ] Troubleshoot common issues

**Estimated completion time:** 20-30 minutes for first-time setup
