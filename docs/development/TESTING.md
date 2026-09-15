# Python 3 Integration Module - Testing Guide

This guide will help you test the Python 3 Integration module after installation in your Ignition 8.3 Gateway.

## Script Console Usage

**Use the Designer Script Console** with a Gateway Scripting Project:

✅ **Designer Script Console** (with Gateway project):
- Designer → Tools → **Script Console**
- Make sure your project is set as a **Gateway Scripting Project**
- Python execution happens on the Gateway server via the process pool

**How it works:** The module is available in Gateway, Client, and Designer scopes. When you run scripts from Designer with a Gateway Scripting Project, the `system.python3.*` functions execute on the Gateway where the Python process pool runs.

## Module Overview

The Python 3 Integration module provides:
- **Python 3 scripting functions** available under `system.python3.*`
- **Subprocess process pool** for executing Python code
- **Gateway-side Python execution** for server-side scripts
- **JSON-based communication** between Ignition and Python

## Prerequisites

Before testing, ensure:
- ✅ Module is installed in Gateway (Config → System → Modules)
- ✅ Gateway has restarted after module installation
- ✅ Python 3.8+ is installed on the Gateway server (or auto-download enabled)

## Step 1: Verify Module Installation

### Check Module Status

1. Open Gateway web interface: `http://localhost:9088` (or your Gateway URL)
2. Navigate to: **Config → System → Modules**
3. Find **Python 3 Integration** module
4. Verify status shows **"Running"**

**Expected Details:**
- **Name**: Python 3 Integration
- **Module ID**: com.gaskony.python3
- **Vendor**: Gaskony
- **Version**: 1.6.1
- **Status**: Running

### Check Gateway Logs

Check the Gateway logs for module startup messages:

**Docker:**
```bash
docker logs <container-name> | grep -i python3
```

**Expected Log Messages:**
```
INFO  [GatewayHook] Python 3 Integration module setup
INFO  [GatewayHook] Python 3 Integration module startup
INFO  [GatewayHook] Using Python: /path/to/python3
INFO  [GatewayHook] Initializing Python 3 process pool (size: 3)
INFO  [GatewayHook] Python 3 Integration module started successfully
INFO  [GatewayHook] Registering Python 3 scripting functions
INFO  [GatewayHook] Python 3 scripting functions registered successfully
```

**If Python 3 is NOT found**, you'll see:
```
ERROR [GatewayHook] Failed to initialize Python 3 process pool
ERROR [GatewayHook] Options:
ERROR [GatewayHook]   1. Install Python 3.8+ on this server
ERROR [GatewayHook]   2. Enable auto-download: -Dignition.python3.autodownload=true
ERROR [GatewayHook]   3. Specify Python path: -Dignition.python3.path=/path/to/python3
```

## Step 2: Test in Designer Script Console

### Access Designer Script Console

1. Open Ignition Designer
2. Go to: **Tools → Script Console**
3. **IMPORTANT**: Make sure your project is configured as a **Gateway Scripting Project**
   - Project Properties → General → Project Scope → **Gateway**

### Test 1: Check Availability

Run this script to verify Python 3 is available:

```python
# Check if Python 3 is available
available = system.python3.isAvailable()
print("Python 3 Available:", available)

if available:
    print("✅ Python 3 is ready!")
else:
    print("❌ Python 3 is not available")
```

**Expected Output:**
```
Python 3 Available: true
✅ Python 3 is ready!
```

### Test 2: Get Python Version

Check what version of Python 3 is running:

```python
# Get Python version information
versionInfo = system.python3.getVersion()
print("Version Info:", versionInfo)
```

**Expected Output:**
```
Version Info: {version=3.10.12 (main, ...), available=true}
```

### Test 3: Run Example Function

Test the built-in example:

```python
# Run the example function
result = system.python3.example()
print(result)
```

**Expected Output:**
```
Python 3 is working! 2^100 = 1267650600228229401496703205376
```

### Test 4: Evaluate Simple Expression

Evaluate a Python expression:

```python
# Evaluate a Python expression
result = system.python3.eval("2 + 2")
print("2 + 2 =", result)

# More complex expression
result = system.python3.eval("sum([1, 2, 3, 4, 5])")
print("Sum of [1,2,3,4,5] =", result)
```

**Expected Output:**
```
2 + 2 = 4
Sum of [1,2,3,4,5] = 15
```

### Test 5: Execute Python Code

Execute multi-line Python code:

```python
# Execute Python code
code = """
import math
x = math.pi
result = round(x, 2)
"""

result = system.python3.exec(code)
print("Value of pi (rounded):", result)
```

**Expected Output:**
```
Value of pi (rounded): 3.14
```

### Test 6: Pass Variables to Python

Execute Python code with variables from Ignition:

```python
# Pass variables to Python
variables = {
    "name": "John",
    "age": 30,
    "values": [1, 2, 3, 4, 5]
}

code = """
message = f"Hello {name}, you are {age} years old"
total = sum(values)
result = {"message": message, "total": total}
"""

result = system.python3.exec(code, variables)
print("Result:", result)
```

**Expected Output:**
```
Result: {message=Hello John, you are 30 years old, total=15}
```

### Test 7: Call Python Module Functions

Call standard library functions:

```python
# Call math.sqrt()
import java.util.ArrayList
args = java.util.ArrayList()
args.add(16)

result = system.python3.callModule("math", "sqrt", args)
print("sqrt(16) =", result)

# Call datetime.now()
args = java.util.ArrayList()  # Empty args list
result = system.python3.callModule("datetime", "datetime.now", args)
print("Current datetime:", result)
```

**Expected Output:**
```
sqrt(16) = 4.0
Current datetime: <datetime object>
```

### Test 8: Get Process Pool Statistics

Check the health of the Python process pool:

```python
# Get pool statistics
stats = system.python3.getPoolStats()
print("Pool Size:", stats['totalSize'])
print("Available:", stats['available'])
print("In Use:", stats['inUse'])
print("Healthy:", stats['healthy'])
```

**Expected Output:**
```
Pool Size: 3
Available: 3
In Use: 0
Healthy: true
```

### Test 9: Get Distribution Info

Check Python distribution details:

```python
# Get distribution information
info = system.python3.getDistributionInfo()
print("Distribution Info:")
for key in info.keySet():
    print("  %s: %s" % (key, info[key]))
```

**Expected Output:**
```
Distribution Info:
  pythonPath: /usr/bin/python3
  version: 3.10.12
  available: true
```

## Step 3: Test in Gateway Scripts

### Test in Timer Script

1. Go to: **Config → System → Gateway Events → Timer Scripts**
2. Create a new timer script:

```python
# Gateway timer script - runs every 60 seconds
logger = system.util.getLogger("Python3.Test")

try:
    # Simple Python 3 calculation
    result = system.python3.eval("2 ** 10")
    logger.info("Python 3 result: 2^10 = " + str(result))

except Exception as e:
    logger.error("Python 3 error: " + str(e))
```

3. Set interval to 60000ms (60 seconds)
4. Save and enable the script
5. Check Gateway logs for output

### Test in Tag Change Script

1. Create a test memory tag: **Test/Python3Input** (Integer type)
2. Add a Tag Change Script:

```python
# Tag change script - runs when tag value changes
if currentValue.value is not None:
    try:
        # Calculate factorial in Python 3
        code = """
import math
result = math.factorial(n)
"""
        variables = {"n": currentValue.value}

        result = system.python3.exec(code, variables)

        # Write result to another tag
        system.tag.writeBlocking(["Test/Python3Output"], [result])

    except Exception as e:
        print("Error: " + str(e))
```

3. Create output tag: **Test/Python3Output** (Integer type)
4. Change **Python3Input** value to 5
5. Check **Python3Output** shows 120 (5! = 120)

## Step 4: Test Error Handling

### Test Invalid Python Code

```python
# This should raise an error
try:
    result = system.python3.exec("this is invalid python")
    print("Result:", result)
except Exception as e:
    print("Caught expected error:", str(e))
```

**Expected Output:**
```
Caught expected error: Python error: invalid syntax (<string>, line 1)
```

### Test Runtime Error

```python
# This should raise a runtime error
try:
    result = system.python3.eval("1 / 0")
    print("Result:", result)
except Exception as e:
    print("Caught expected error:", str(e))
```

**Expected Output:**
```
Caught expected error: Python error: division by zero
```

## Step 5: Performance Testing

### Test Process Pool Reuse

```python
# Execute multiple operations to test pool
import time

startTime = time.time()

for i in range(10):
    result = system.python3.eval("2 ** %d" % i)
    print("2^%d = %s" % (i, result))

elapsed = (time.time() - startTime) * 1000
print("\nCompleted 10 operations in %.2f ms" % elapsed)
```

This tests that the process pool correctly reuses Python interpreters.

### Test Concurrent Execution

```python
# Test multiple concurrent Python calls
def runPythonTask(taskNum):
    code = """
import time
time.sleep(0.1)  # Simulate work
result = taskNum * 2
"""
    variables = {"taskNum": taskNum}
    return system.python3.exec(code, variables)

# Run multiple tasks
import time
startTime = time.time()

results = []
for i in range(5):
    result = runPythonTask(i)
    results.append(result)

elapsed = (time.time() - startTime)
print("Results:", results)
print("Time: %.2f seconds" % elapsed)
print("(Should be ~0.2s with pool size 3, not 0.5s)")
```

## Troubleshooting

### Module Not Loading

**Symptoms:**
- Module shows error status
- Module not visible in modules list

**Solutions:**
1. Check Ignition version is 8.3.0+
2. Check Gateway logs for errors
3. Verify module is signed (or unsigned modules are allowed)

### Python 3 Not Available

**Symptoms:**
- `system.python3.isAvailable()` returns `false`
- Error in logs: "Failed to initialize Python 3 process pool"

**Solutions:**

**Option 1: Install Python 3.8+**
```bash
# Ubuntu/Debian
sudo apt update && sudo apt install python3

# RedHat/CentOS
sudo yum install python3

# Verify installation
python3 --version
```

**Option 2: Enable Auto-Download**

Add to `ignition.conf`:
```
wrapper.java.additional.999=-Dignition.python3.autodownload=true
```

**Option 3: Specify Python Path**

Add to `ignition.conf`:
```
wrapper.java.additional.999=-Dignition.python3.path=/usr/bin/python3
```

After changes, restart Gateway:
```bash
docker restart <container-name>
# or
sudo systemctl restart ignition
```

### Scripting Functions Not Available

**Symptoms:**
- `system.python3` is undefined
- AttributeError in scripts

**Solutions:**
1. Check module status is "Running"
2. Check logs for "Registering Python 3 scripting functions"
3. Restart Gateway
4. Restart Designer (close and reopen)

### Performance Issues

**Symptoms:**
- Python calls take a long time
- Scripts timeout

**Solutions:**

Increase pool size in `ignition.conf`:
```
wrapper.java.additional.999=-Dignition.python3.poolsize=5
```

Check pool stats:
```python
stats = system.python3.getPoolStats()
print("In Use:", stats['inUse'], "/ Total:", stats['totalSize'])
```

## Configuration Options

Add these to `ignition.conf` under `[wrapper.java.additional.N]` section:

| Property | Default | Description |
|----------|---------|-------------|
| `ignition.python3.poolsize` | `3` | Number of Python processes in pool |
| `ignition.python3.autodownload` | `true` | Auto-download Python if not found |
| `ignition.python3.path` | (auto) | Explicit path to python3 executable |

**Example ignition.conf:**
```ini
wrapper.java.additional.998=-Dignition.python3.poolsize=5
wrapper.java.additional.999=-Dignition.python3.path=/usr/bin/python3
```

## Advanced Testing

### Test with External Libraries

If you need external Python packages:

1. Install packages where Python can find them:
```bash
python3 -m pip install requests pandas numpy
```

2. Test in Ignition:
```python
# Test requests library
code = """
import requests
response = requests.get('http://httpbin.org/json')
result = response.status_code
"""

result = system.python3.exec(code)
print("HTTP Status:", result)  # Should be 200
```

### Test Complex Data Structures

```python
# Pass and return complex data
code = """
import json

# Process incoming data
data = inputData
processed = {
    'sum': sum(data['numbers']),
    'avg': sum(data['numbers']) / len(data['numbers']),
    'max': max(data['numbers']),
    'min': min(data['numbers'])
}

result = processed
"""

variables = {
    'inputData': {
        'numbers': [10, 20, 30, 40, 50],
        'name': 'Test Dataset'
    }
}

result = system.python3.exec(code, variables)
print("Statistics:", result)
```

## Success Criteria

Your module is working correctly if:

- ✅ `system.python3.isAvailable()` returns `true`
- ✅ `system.python3.example()` returns expected result
- ✅ `system.python3.eval()` can evaluate expressions
- ✅ `system.python3.exec()` can execute code
- ✅ `system.python3.callModule()` can call Python functions
- ✅ Pool statistics show healthy processes
- ✅ Gateway logs show successful startup
- ✅ No errors in Gateway logs

## Next Steps

Once testing is complete:

1. **Use in Production Scripts**: Integrate Python 3 calls into your Gateway scripts
2. **Monitor Performance**: Check pool statistics regularly
3. **Install Python Packages**: Add any required Python libraries
4. **Scale Pool Size**: Adjust pool size based on usage
5. **Create Documentation**: Document any custom Python functions

## Support

For issues or questions:
- Check Gateway logs: `logs/wrapper.log`
- Review module documentation: `SIGNING.md`, `README.md`
- GitHub: https://github.com/Gaskony-Ignition/ignition-module-python3

---

**Developed by Gaskony**
