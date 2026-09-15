# Troubleshooting Guide

**Module:** Python 3 Integration for Ignition 8.3+

Comprehensive troubleshooting guide for common issues and their solutions.

---

## Table of Contents

1. [Quick Diagnosis](#quick-diagnosis)
2. [Installation Issues](#installation-issues)
3. [Module Loading Issues](#module-loading-issues)
4. [Connection Issues](#connection-issues)
5. [Execution Issues](#execution-issues)
6. [Performance Issues](#performance-issues)
7. [IDE Issues](#ide-issues)
8. [REST API Issues](#rest-api-issues)
9. [Python Environment Issues](#python-environment-issues)
10. [Build and Development Issues](#build-and-development-issues)

---

## Quick Diagnosis

### Is the Module Working?

Run these quick checks to diagnose the issue:

#### 1. Check Module Status
**Gateway Web Interface:**
- Navigate to: Config → System → Modules
- Find: "Python 3 Integration"
- Status should show: **"Running"** (green)

**If status shows "Failed" or "Error":**
- Check Gateway logs: `<ignition-install>/logs/wrapper.log`
- Look for errors containing "Python3"

#### 2. Check Python Availability
**From terminal:**
```bash
# Check Python installation
python3 --version
# Expected: Python 3.9.x, 3.11.x, or 3.12.x

# Check Python path
which python3
# Expected: /usr/bin/python3 or /usr/local/bin/python3
```

#### 3. Test Basic Execution
**Script Console:**
```python
# Test 1: Get version
print system.python3.getVersion()
# Expected: {'version': '3.x.x', 'executable': '/path/to/python3'}

# Test 2: Execute simple code
result = system.python3.exec("result = 2 + 2")
print result
# Expected: 4

# Test 3: Check pool stats
stats = system.python3.getPoolStats()
print stats
# Expected: {'poolSize': 3, 'availableExecutors': 3, ...}
```

#### 4. Check REST API
**From terminal:**
```bash
curl http://localhost:8088/data/python3integration/api/v1/health
# Expected: {"status":"healthy","poolSize":3}
```

---

## Installation Issues

### Issue: Build Fails with "Command not found"

**Symptoms:**
```
./gradlew: command not found
```

**Cause:** Gradle wrapper not executable or missing

**Solutions:**
```bash
# Make wrapper executable
chmod +x gradlew

# If wrapper is missing, regenerate it
gradle wrapper --gradle-version 8.10.2
```

---

### Issue: Build Fails with Java Version Error

**Symptoms:**
```
ERROR: JAVA_HOME is set to an invalid directory
or
Unsupported class file major version 61
```

**Cause:** Wrong Java version (need Java 17+)

**Solutions:**
```bash
# Check current Java version
java -version

# If wrong version, set JAVA_HOME
export JAVA_HOME=/path/to/jdk-17
export PATH=$JAVA_HOME/bin:$PATH

# Verify
java -version
# Expected: java version "17.x.x" or higher
```

---

### Issue: Module Install Fails with "Invalid Signature"

**Symptoms:**
- Module file rejected during installation
- Error: "Invalid module signature"

**Cause:** Using unsigned .modl file or corrupted build

**Solutions:**
```bash
# Rebuild with clean
cd python3-integration
./gradlew clean build --no-daemon

# Use SIGNED module file
ls -lh build/libs/python3-integration-signed.modl

# If unsigned, check for certificate files
ls -lh certificate.der keystore.jks sign.props
```

---

### Issue: Module Fails to Load After Install

**Symptoms:**
- Module installs but shows "Failed" status
- Gateway logs show errors during module startup

**Cause:** Various - check logs for specific error

**Solutions:**

1. **Check wrapper.log:**
   ```bash
   tail -100 <ignition-install>/logs/wrapper.log | grep -i python3
   ```

2. **Common errors and fixes:**
   - "Python executable not found" → See [Python Environment Issues](#python-environment-issues)
   - "Permission denied" → Check Python executable permissions
   - "ClassNotFoundException" → Module dependency issue, reinstall module
   - "OutOfMemoryError" → Increase Gateway heap size in ignition.conf

---

## Module Loading Issues

### Issue: Module Won't Load - Python Executable Not Found

**Symptoms:**
```
ERROR [GatewayHook] Failed to initialize Python 3 process pool
ERROR [Python3ProcessPool] Python executable not found at path: python3
```

**Cause:** Python 3 not installed or not in PATH

**Solutions:**

1. **Install Python 3:**
   ```bash
   # Ubuntu/Debian
   sudo apt-get update
   sudo apt-get install python3 python3-pip

   # CentOS/RHEL
   sudo yum install python3 python3-pip

   # macOS
   brew install python@3.11

   # Windows
   # Download from python.org and install
   ```

2. **Configure Python path manually:**
   Edit `<ignition-install>/data/ignition.conf`:
   ```properties
   # Add after existing wrapper.java.additional.* lines
   wrapper.java.additional.101=-Dignition.python3.path=/usr/bin/python3
   ```

3. **Verify Python path:**
   ```bash
   which python3
   # Copy this path to ignition.conf
   ```

4. **Restart Gateway** after editing ignition.conf

---

### Issue: Module Won't Load - Permission Denied

**Symptoms:**
```
ERROR [Python3Executor] Failed to start Python process
java.io.IOException: Cannot run program "python3": error=13, Permission denied
```

**Cause:** Python executable not executable by Ignition user

**Solutions:**
```bash
# Check Python executable permissions
ls -l $(which python3)
# Should show: -rwxr-xr-x (executable)

# Make executable if needed
sudo chmod +x /usr/bin/python3

# Check if Ignition user can execute
sudo -u ignition python3 --version
# Should show Python version without error
```

---

### Issue: Module Loads But Pool Fails to Initialize

**Symptoms:**
```
INFO  [GatewayHook] Module loaded successfully
ERROR [Python3ProcessPool] Failed to create executor: Process exited with code 1
```

**Cause:** Python process starts but exits immediately

**Solutions:**

1. **Test Python manually:**
   ```bash
   python3 -c "print('Hello')"
   # Should print: Hello
   ```

2. **Check Python dependencies:**
   ```bash
   python3 -c "import json, sys; print('OK')"
   # Should print: OK
   ```

3. **Check python_bridge.py:**
   ```bash
   # Extract bridge script from logs
   grep "python_bridge.py" <ignition-install>/logs/wrapper.log

   # Test bridge directly
   echo '{"command":"execute","code":"result=1","variables":{}}' | python3 /path/to/python_bridge.py
   # Should return: {"success":true,"result":1}
   ```

---

## Connection Issues

### Issue: IDE Won't Connect to Gateway

**Symptoms:**
- Click "Connect" in Python 3 IDE
- Error: "Connection failed"
- Status bar shows: "Not connected"

**Cause:** Gateway URL incorrect or Gateway not reachable

**Solutions:**

1. **Verify Gateway URL format:**
   - Correct: `http://<gateway-host>:8088` (a hostname/IP, with scheme)
   - Wrong: `localhost:8088` (missing http://)
   - Wrong: `https://localhost:8088` (unless SSL configured)

2. **Test Gateway reachability:**
   ```bash
   # Test web interface
   curl http://localhost:8088
   # Should return HTML

   # Test health endpoint
   curl http://localhost:8088/data/python3integration/api/v1/health
   # Should return: {"status":"healthy",...}
   ```

3. **Check network connectivity:**
   ```bash
   # Ping Gateway
   ping -c 3 localhost

   # Check port is open
   telnet localhost 8088
   # Should connect
   ```

4. **Check firewall:**
   ```bash
   # Ubuntu/Debian
   sudo ufw status
   sudo ufw allow 8088/tcp

   # CentOS/RHEL
   sudo firewall-cmd --list-ports
   sudo firewall-cmd --add-port=8088/tcp --permanent
   sudo firewall-cmd --reload
   ```

---

### Issue: Connection Succeeds But Script Tree Empty

**Symptoms:**
- IDE connects successfully
- Script tree shows only "Scripts" root node
- No scripts appear

**Cause:** No scripts saved yet OR REST API returning empty list

**Solutions:**

1. **Create a test script:**
   - Write code in editor
   - Enter script name in metadata panel
   - Click "Save"
   - Refresh script tree

2. **Test REST API directly:**
   ```bash
   curl http://localhost:8088/data/python3integration/api/v1/scripts
   # Expected: [] (empty array if no scripts)
   # or: [{"name":"script1","author":"user",...}]
   ```

3. **Check Gateway logs for errors:**
   ```bash
   tail -f <ignition-install>/logs/wrapper.log | grep -i "script"
   ```

---

## Execution Issues

### Issue: Code Execution Times Out

**Symptoms:**
```
ERROR: Execution timed out after 30 seconds
```

**Cause:** Script takes longer than default 30-second timeout

**Solutions:**

1. **Increase timeout globally:**
   Edit `<ignition-install>/data/ignition.conf`:
   ```properties
   wrapper.java.additional.103=-Dignition.python3.timeout=60
   ```
   Restart Gateway.

2. **Optimize long-running code:**
   ```python
   # Instead of:
   for i in range(10000000):
       process(i)  # Very slow

   # Use:
   import multiprocessing
   with multiprocessing.Pool() as pool:
       pool.map(process, range(10000000))  # Faster
   ```

3. **Split into smaller chunks:**
   ```python
   # Process in batches
   batch_size = 1000
   for i in range(0, total, batch_size):
       batch = data[i:i+batch_size]
       process_batch(batch)
   ```

---

### Issue: Execution Fails with "Pool Exhausted"

**Symptoms:**
```
ERROR: Timeout waiting for available executor (30 seconds)
ERROR: No executors available in pool
```

**Cause:** All processes in pool busy with long-running operations

**Solutions:**

1. **Increase pool size:**
   Edit `<ignition-install>/data/ignition.conf`:
   ```properties
   wrapper.java.additional.102=-Dignition.python3.poolsize=5
   ```
   Restart Gateway.

2. **Check pool statistics:**
   ```python
   stats = system.python3.getPoolStats()
   print stats
   # Look for: poolSize vs availableExecutors
   ```

3. **Identify stuck processes:**
   ```bash
   # Check Gateway logs
   grep "borrowExecutor" <ignition-install>/logs/wrapper.log

   # Look for processes that never return
   ```

---

### Issue: Import Errors in Python Code

**Symptoms:**
```python
result = system.python3.exec("import pandas")
# ERROR: ModuleNotFoundError: No module named 'pandas'
```

**Cause:** Python package not installed in Gateway's Python environment

**Solutions:**

1. **Install package:**
   ```bash
   # Find Python executable used by Gateway
   grep "python3.path" <ignition-install>/data/ignition.conf

   # Install package
   /usr/bin/python3 -m pip install pandas

   # Verify
   /usr/bin/python3 -c "import pandas; print(pandas.__version__)"
   ```

2. **Use virtual environment (recommended):**
   ```bash
   # Create venv
   python3 -m venv /opt/ignition-python-env

   # Activate and install packages
   source /opt/ignition-python-env/bin/activate
   pip install pandas numpy requests

   # Configure Ignition to use venv
   # Edit ignition.conf:
   wrapper.java.additional.101=-Dignition.python3.path=/opt/ignition-python-env/bin/python3
   ```

3. **Check package from IDE:**
   - Open Python 3 IDE
   - Tools → Packages
   - Shows all installed packages

---

### Issue: Variable Passing Fails

**Symptoms:**
```python
variables = {"x": 10, "y": 20}
result = system.python3.eval("x + y", variables)
# ERROR: NameError: name 'x' is not defined
```

**Cause:** Variable serialization issue or syntax error

**Solutions:**

1. **Verify variable format:**
   ```python
   # Correct: Dictionary with string keys
   variables = {"x": 10, "y": 20}

   # Wrong: List
   variables = [10, 20]

   # Wrong: Non-string keys
   variables = {1: 10, 2: 20}
   ```

2. **Check data types:**
   ```python
   # Supported types: str, int, float, bool, list, dict, None
   variables = {
       "name": "John",       # str - OK
       "age": 30,            # int - OK
       "height": 5.9,        # float - OK
       "active": True,       # bool - OK
       "items": [1, 2, 3],   # list - OK
       "data": {"a": 1}      # dict - OK
   }

   # Unsupported: complex objects
   variables = {
       "date": system.date.now()  # Wrong - Java Date object
   }
   ```

3. **Test variable passing:**
   ```python
   # Simple test
   result = system.python3.eval("x", {"x": 42})
   print result  # Should print: 42
   ```

---

## Performance Issues

### Issue: Slow Execution Times

**Symptoms:**
- Code takes much longer to execute than expected
- Simple operations take several seconds

**Diagnosis:**
```python
# Measure execution time
code = "result = sum(range(1000000))"
import time
start = time.time()
result = system.python3.exec(code)
elapsed = time.time() - start
print "Execution time:", elapsed, "seconds"
```

**Solutions:**

1. **Check pool statistics:**
   ```python
   stats = system.python3.getPoolStats()
   print "Available executors:", stats['availableExecutors']
   print "Total executions:", stats['totalExecutions']

   # If availableExecutors is low, pool may be exhausted
   ```

2. **Optimize Python code:**
   ```python
   # Slow: Using loops
   result = 0
   for i in range(1000000):
       result += i

   # Fast: Using built-in functions
   result = sum(range(1000000))
   ```

3. **Use caching for repeated operations:**
   ```python
   # Cache expensive computations
   import functools

   @functools.lru_cache(maxsize=128)
   def expensive_function(x):
       # ... expensive computation
       return result
   ```

4. **Monitor Gateway resources:**
   ```bash
   # Check CPU usage
   top -p $(pgrep -f ignition)

   # Check memory usage
   free -h

   # Check disk I/O
   iostat -x 1
   ```

---

### Issue: High Memory Usage

**Symptoms:**
- Gateway using excessive memory
- OutOfMemoryError in logs
- System becomes slow

**Diagnosis:**
```bash
# Check Gateway heap usage
grep "OutOfMemoryError" <ignition-install>/logs/wrapper.log

# Check process memory
ps aux | grep ignition
```

**Solutions:**

1. **Increase Gateway heap size:**
   Edit `<ignition-install>/data/ignition.conf`:
   ```properties
   # Increase max heap (example: 4GB)
   wrapper.java.maxmemory=4096
   ```
   Restart Gateway.

2. **Reduce pool size:**
   ```properties
   # Fewer Python processes = less memory
   wrapper.java.additional.102=-Dignition.python3.poolsize=3
   ```

3. **Optimize Python code to use less memory:**
   ```python
   # Slow + Memory-intensive: Load all data at once
   data = load_huge_file()  # Loads 1GB into memory
   process(data)

   # Fast + Memory-efficient: Process in chunks
   with open('huge_file.txt') as f:
       for chunk in read_in_chunks(f, 1024):
           process(chunk)
   ```

---

## IDE Issues

### Issue: IDE Window Blank or Not Rendering

**Symptoms:**
- Python 3 IDE window opens but is blank
- UI elements not visible
- Window appears frozen

**Solutions:**

1. **Restart Designer:**
   - Close Designer completely
   - Reopen Designer
   - Try Tools → Python 3 IDE again

2. **Check Java version:**
   ```bash
   java -version
   # Must be Java 17+ for proper Swing rendering
   ```

3. **Check Designer logs:**
   - Look for exceptions during IDE initialization
   - Check for theme-related errors

4. **Reset IDE preferences:**
   ```bash
   # Delete IDE preferences file
   rm ~/.python3ide/preferences.properties

   # Reopen IDE (will recreate with defaults)
   ```

---

### Issue: Code Editor Not Syntax Highlighting

**Symptoms:**
- Code appears as plain text (black on white)
- No Python syntax colouring

**Solutions:**

1. **Check theme setting:**
   - IDE → Theme dropdown (top right)
   - Select "Dark" or "VS Code Dark+"

2. **Verify RSyntaxTextArea installation:**
   - Module should include RSyntaxTextArea dependency
   - Check module dependencies in build.gradle.kts

3. **Restart IDE window:**
   - Close Python 3 IDE window
   - Reopen: Tools → Python 3 IDE

---

### Issue: Scripts Won't Save

**Symptoms:**
- Click "Save" button
- No error message but script doesn't appear in tree
- Script tree doesn't refresh

**Solutions:**

1. **Check script name:**
   - Name field must not be empty
   - Name must be valid (alphanumeric, spaces, dashes, underscores)
   - No special characters: / \ : * ? " < > |

2. **Check Gateway connection:**
   - Status bar should show "Connected"
   - If not, click "Connect" button first

3. **Test REST API:**
   ```bash
   # Test save endpoint
   curl -X POST http://localhost:8088/data/python3integration/api/v1/scripts/save \
     -H "Content-Type: application/json" \
     -d '{
       "name": "test_script",
       "code": "print(\"Hello\")",
       "description": "Test",
       "author": "user",
       "folderPath": "",
       "version": "1.0"
     }'
   ```

4. **Check Gateway logs:**
   ```bash
   tail -f <ignition-install>/logs/wrapper.log | grep -i "save"
   ```

---

### Issue: Find/Replace Not Working

**Symptoms:**
- Click Find button, nothing happens
- Find toolbar doesn't appear

**Solutions:**

1. **Use keyboard shortcut:**
   - Press **Ctrl+F** (Windows/Linux) or **Cmd+F** (Mac)
   - Find toolbar should appear below editor

2. **Check if toolbar is collapsed:**
   - Look for small arrow at bottom of editor
   - Click to expand toolbar

3. **Verify code in editor:**
   - Find only works if there's code in editor
   - Type some text and try again

---

## REST API Issues

### Issue: API Returns 404 Not Found

**Symptoms:**
```bash
curl http://localhost:8088/data/python3integration/api/v1/health
# HTTP/1.1 404 Not Found
```

**Cause:** Module not loaded or routes not mounted

**Solutions:**

1. **Check module status:**
   - Gateway → Config → System → Modules
   - Python 3 Integration should show "Running"

2. **Check Gateway logs for route mounting:**
   ```bash
   grep "Mounting route" <ignition-install>/logs/wrapper.log
   # Should see: Mounting route: /data/python3integration/api/v1/...
   ```

3. **Verify URL format:**
   ```bash
   # Correct URL structure
   http://localhost:8088/data/python3integration/api/v1/{endpoint}

   # Common mistakes:
   # Wrong: /api/v1/health (missing /data/python3integration)
   # Wrong: /python3/api/v1/health (wrong module path)
   ```

---

### Issue: API Returns 500 Internal Server Error

**Symptoms:**
```bash
curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
  -H "Content-Type: application/json" \
  -d '{"code": "result = 2 + 2"}'
# HTTP/1.1 500 Internal Server Error
```

**Cause:** Exception during code execution

**Solutions:**

1. **Check request format:**
   ```bash
   # Correct format (note: variables is required)
   curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
     -H "Content-Type: application/json" \
     -d '{"code": "result = 2 + 2", "variables": {}}'
   ```

2. **Check Gateway logs:**
   ```bash
   tail -50 <ignition-install>/logs/wrapper.log
   # Look for Java stack traces
   ```

3. **Test with minimal code:**
   ```bash
   # Simplest possible test
   curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
     -H "Content-Type: application/json" \
     -d '{"code": "result = 1", "variables": {}}'
   ```

---

## Python Environment Issues

### Issue: Multiple Python Versions Causing Conflicts

**Symptoms:**
- Gateway logs show different Python version than expected
- Import errors for packages installed in different Python version

**Solutions:**

1. **Check active Python version:**
   ```bash
   python3 --version
   /usr/bin/python3 --version
   /usr/local/bin/python3 --version

   # May show different versions
   ```

2. **Configure specific Python version:**
   ```bash
   # Find all Python installations
   ls -l /usr/bin/python3*
   ls -l /usr/local/bin/python3*

   # Choose one and configure in ignition.conf
   wrapper.java.additional.101=-Dignition.python3.path=/usr/bin/python3.11
   ```

3. **Use virtual environment (best practice):**
   ```bash
   # Create venv with specific Python version
   /usr/bin/python3.11 -m venv /opt/ignition-python-env

   # Configure Ignition to use it
   wrapper.java.additional.101=-Dignition.python3.path=/opt/ignition-python-env/bin/python3
   ```

---

### Issue: Python Package Version Conflicts

**Symptoms:**
```
ImportError: cannot import name 'SomeClass' from 'package'
AttributeError: module 'package' has no attribute 'method'
```

**Cause:** Package version incompatibility

**Solutions:**

1. **Check installed package versions:**
   ```bash
   python3 -m pip list
   python3 -m pip show <package-name>
   ```

2. **Install specific version:**
   ```bash
   python3 -m pip install <package>==<version>
   # Example: pip install pandas==1.5.3
   ```

3. **Use requirements.txt:**
   ```bash
   # Create requirements.txt
   cat > requirements.txt << EOF
   pandas==1.5.3
   numpy==1.24.3
   requests==2.31.0
   EOF

   # Install exact versions
   python3 -m pip install -r requirements.txt
   ```

---

## Build and Development Issues

### Issue: Checkstyle Violations Prevent Build

**Symptoms:**
```
BUILD FAILED
Checkstyle violations detected
```

**Solutions:**

1. **View Checkstyle report:**
   ```bash
   # Build generates report
   ./gradlew checkstyleMain

   # View report
   open build/reports/checkstyle/main.html
   ```

2. **Common violations and fixes:**
   ```java
   // Violation: Star import
   import java.util.*;
   // Fix: Explicit imports
   import java.util.List;
   import java.util.Map;

   // Violation: Missing Javadoc
   public class MyClass {
   // Fix: Add Javadoc
   /**
    * Description of MyClass.
    */
   public class MyClass {
   ```

3. **Disable Checkstyle temporarily (not recommended):**
   ```bash
   ./gradlew build -x checkstyleMain
   ```

---

### Issue: Tests Fail During Build

**Symptoms:**
```
BUILD FAILED
Tests failed
```

**Solutions:**

1. **Run tests with verbose output:**
   ```bash
   ./gradlew test --info
   ```

2. **View test report:**
   ```bash
   open build/reports/tests/test/index.html
   ```

3. **Run specific test:**
   ```bash
   ./gradlew test --tests "Python3ExecutorTest"
   ./gradlew test --tests "Python3ExecutorTest.testExecuteCode"
   ```

4. **Skip tests temporarily (not recommended):**
   ```bash
   ./gradlew build -x test
   ```

---

## Getting Help

If you've tried the solutions above and still have issues:

### 1. Gather Diagnostic Information

Collect the following information:

```bash
# Gateway information
cat <ignition-install>/data/.install4j/response.varfile

# Module version
ls -lh <ignition-install>/user-lib/modules/python3-integration*.modl

# Python version
python3 --version
which python3

# Gateway logs (last 100 lines with Python3)
tail -100 <ignition-install>/logs/wrapper.log | grep -i python3

# Pool statistics (from Script Console)
print system.python3.getPoolStats()

# System information
uname -a
java -version
```

### 2. Check Documentation

- **Architecture Guide:** [../V2_ARCHITECTURE_GUIDE.md](../V2_ARCHITECTURE_GUIDE.md)
- **Quick Start:** [../getting-started/QUICK_START.md](../getting-started/QUICK_START.md)
- **Testing Guide:** [../TESTING_GUIDE.md](../TESTING_GUIDE.md)

### 3. Search Ignition Forum

- **Forum:** https://forum.inductiveautomation.com/
- Search for similar issues
- Module Development category

### 4. Open GitHub Issue

- **Repository:** https://github.com/Gaskony-Ignition/ignition-module-python3
- Include diagnostic information above
- Describe steps to reproduce
- Include error messages and logs

---

## Appendix: Common Error Messages

### Error Message Reference

| Error Message | Likely Cause | Solution Section |
|--------------|--------------|------------------|
| "Python executable not found" | Python not installed or not in PATH | [Module Loading Issues](#module-loading-issues) |
| "Timeout waiting for executor" | Pool exhausted | [Execution Issues](#execution-issues) |
| "ModuleNotFoundError" | Python package not installed | [Execution Issues](#execution-issues) |
| "Connection failed" | Gateway URL incorrect | [Connection Issues](#connection-issues) |
| "Invalid module signature" | Using unsigned .modl | [Installation Issues](#installation-issues) |
| "Permission denied" | Python not executable | [Module Loading Issues](#module-loading-issues) |
| "OutOfMemoryError" | Insufficient heap size | [Performance Issues](#performance-issues) |
| "404 Not Found" | Module not loaded or wrong URL | [REST API Issues](#rest-api-issues) |

---

**Maintained By:** Development Team
