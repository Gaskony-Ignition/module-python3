# Python 3 IDE - Designer User Guide

**Audience:** Ignition Designer Users

---

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Opening the Python 3 IDE](#opening-the-python-3-ide)
4. [Understanding the Interface](#understanding-the-interface)
5. [Writing Your First Script](#writing-your-first-script)
6. [Working with Scripts](#working-with-scripts)
7. [Execution Modes](#execution-modes)
8. [Available Python Modules](#available-python-modules)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)
11. [Keyboard Shortcuts](#keyboard-shortcuts)

---

## Introduction

The **Python 3 IDE** is a powerful development environment integrated directly into the Ignition Designer. It allows you to write, test, and execute Python 3 code with full access to modern Python libraries and capabilities.

### Key Features

- ✅ **Modern Python 3** - Use Python 3.11+ with all modern syntax and features
- ✅ **Full Module Access** - Import os, sys, subprocess, pandas, numpy, and more
- ✅ **Syntax Highlighting** - Colour-coded Python syntax for better readability
- ✅ **Script Management** - Save, organise, and reuse scripts in folders
- ✅ **Real-time Execution** - Execute code and see results immediately
- ✅ **Error Reporting** - Clear error messages with stack traces
- ✅ **Gateway Integration** - Execute code on the Gateway with full permissions
- ✅ **Dark/Light Themes** - Choose your preferred colour scheme

### What Makes This Different from Script Console?

| Feature | Python 3 IDE | Script Console |
|---------|-------------|----------------|
| Python Version | **Python 3.11+** | Jython 2.7 |
| Modern Syntax | ✅ Yes (f-strings, type hints, etc.) | ❌ No |
| External Libraries | ✅ Yes (admin-installed via Gateway web UI) | ❌ Limited |
| OS/System Access | ✅ Full access | ❌ Restricted |
| Performance | ✅ Fast (native Python) | 🟡 Slower (JVM) |
| Pandas/NumPy | ✅ Yes | ❌ No |

---

## Getting Started

### Prerequisites

1. **Ignition Designer** - Version 8.3 or later
2. **Python 3 Integration Module** - Installed on Gateway
3. **Gateway Connection** - Active connection to Gateway

### Installation

The Python 3 Integration module must be installed on your Gateway:

1. Download the module: `Python3Integration-2.6.0.modl`
2. Navigate to **Gateway Config → System → Modules**
3. Click **Install or Upgrade a Module**
4. Select the `.modl` file and click **Install**
5. Restart the Gateway when prompted
6. Verify installation: Look for "Python 3 Integration" in the module list

---

## Opening the Python 3 IDE

### Method 1: Via Menu

1. Open the **Ignition Designer**
2. Click **Tools** in the top menu
3. Select **Python 3 Script Console**
4. The IDE window will open

### Method 2: Via Keyboard Shortcut

- **Windows/Linux**: `Ctrl + Shift + P`
- **Mac**: `Cmd + Shift + P`

*(Note: Keyboard shortcut may need to be configured in Designer preferences)*

---

## Understanding the Interface

The Python 3 IDE has a clean, organised layout:

```
┌─────────────────────────────────────────────────────────────┐
│ Python 3 IDE v2.6.0                                    [x]  │
├─────────────────────────────────────────────────────────────┤
│ File  Edit  View  Tools  Help                              │
├──────────────┬──────────────────────────────────────────────┤
│              │  Script Name: [my_script                  ]  │
│  Script      │  Description: [Data processing script     ]  │
│  Browser     │                                              │
│              │  ┌────────────────────────────────────────┐  │
│  □ Scripts   │  │ # Python 3 Code Editor                │  │
│    □ Folder1 │  │ import os                             │  │
│      - my_s  │  │ import pandas as pd                   │  │
│    □ Folder2 │  │                                       │  │
│    - test.py │  │ # Your code here...                   │  │
│              │  │                                       │  │
│  [+ New]     │  └────────────────────────────────────────┘  │
│  [Save]      │                                              │
│  [Delete]    │  [Execute Code (Ctrl+Enter)]                 │
│              │                                              │
│              │  ┌─ Output ──────────────────────────────┐  │
│              │  │ Execution completed in 125ms          │  │
│              │  │ Result: Success                       │  │
│              │  └───────────────────────────────────────┘  │
│              │                                              │
│              │  ┌─ Diagnostics ─────────────────────────┐  │
│  Gateway:    │  │ Python Version: 3.11.2                │  │
│  localhost   │  │ Pool: 3 processes (3 healthy)         │  │
│  :8088       │  │ Memory: 128 MB                        │  │
│              │  └───────────────────────────────────────┘  │
│  [Connect]   │                                              │
│              │                                              │
└──────────────┴──────────────────────────────────────────────┘
```

### Main Components

1. **Menu Bar** - File operations, settings, help
2. **Script Browser** - Navigate and manage saved scripts
3. **Script Metadata** - Name and description of current script
4. **Code Editor** - Write Python 3 code with syntax highlighting
5. **Execute Button** - Run code on the Gateway
6. **Output Panel** - View results and execution time
7. **Error Panel** - View error messages and stack traces
8. **Diagnostics Panel** - Gateway info, pool stats, Python version (read-only)
9. **Gateway Connection** - Uses the Designer's own authenticated Gateway
   session automatically (the previous manual Gateway-URL override was
   removed — the Designer always talks to the Gateway it is logged into)

---

## Writing Your First Script

### Example 1: Hello World

```python
# Simple hello world
print("Hello from Python 3!")

# Check Python version
import sys
print(f"Python version: {sys.version}")

# Result will appear in Output panel
```

**To Execute:**
1. Type the code in the editor
2. Click **Execute Code** or press `Ctrl+Enter`
3. View output in the **Output** panel

**Expected Output:**
```
Hello from Python 3!
Python version: 3.11.2 (main, ...)
```

### Example 2: Working with Data

```python
# Import libraries
import pandas as pd
import json

# Create sample data
data = {
    'name': ['Alice', 'Bob', 'Charlie'],
    'age': [25, 30, 35],
    'city': ['New York', 'London', 'Tokyo']
}

# Create DataFrame
df = pd.DataFrame(data)

# Process data
average_age = df['age'].mean()

# Output results
print(f"Average age: {average_age}")
print("\nDataFrame:")
print(df.to_string())

# Return value (appears in Output)
result = df.to_dict()
```

**Expected Output:**
```
Average age: 30.0

DataFrame:
      name  age      city
0    Alice   25  New York
1      Bob   30    London
2  Charlie   35     Tokyo
```

### Example 3: File Operations

```python
import os

# Get current working directory
cwd = os.getcwd()
print(f"Current directory: {cwd}")

# List files
files = os.listdir('.')
print(f"\nFiles in directory: {len(files)}")
for f in files[:10]:  # First 10 files
    print(f"  - {f}")

# Get environment info
print(f"\nOS: {os.name}")
print(f"CPU count: {os.cpu_count()}")
```

---

## Working with Scripts

### Saving Scripts

1. **Enter Script Name** - Type a name in the "Script Name" field
2. **Add Description** - Optional description of what the script does
3. **Click Save** - Script is saved to the Gateway
4. **Organize in Folders** - Right-click in Script Browser → Create Folder

**File Location:**
Scripts are saved on the Gateway at: `<ignition>/data/python3-scripts/`

### Loading Scripts

1. **Browse Scripts** - Navigate Script Browser on the left
2. **Double-click Script** - Opens in editor
3. **Click Load** - Loads selected script

### Deleting Scripts

1. **Select Script** - Click on script in Script Browser
2. **Click Delete** - Confirms before deleting
3. **Permanent** - Cannot be undone

### Organizing with Folders

1. **Right-click** in Script Browser
2. **Select "Create Folder"**
3. **Enter Folder Name**
4. **Drag scripts** into folders

**Example Organization:**
```
Scripts
├── Data Processing
│   ├── import_csv.py
│   ├── export_excel.py
│   └── clean_data.py
├── Utilities
│   ├── backup.py
│   └── health_check.py
└── Testing
    └── test_connection.py
```

### Importing/Exporting Scripts

**Import:**
1. Click **File → Import Script**
2. Select `.py` file from disk
3. Script loaded into editor

**Export:**
1. Open script in editor
2. Click **File → Export Script**
3. Save as `.py` file

---

## Execution Modes

### Python Code Mode (the only mode)

Execute standard Python 3 code with full module access.

```python
# Python Code Mode
import pandas as pd
import requests

# Your code here...
df = pd.read_csv('data.csv')
print(df.head())
```

Need a shell command while developing? Use Python's own `subprocess`
module — prefer an argument list (no `shell=True`):

```python
import subprocess

result = subprocess.run(["ls", "-la"], capture_output=True, text=True)
print(result.stdout)
```

### Shell Command Mode

> **Removed — see `docs/PROJECT_CHARTER.md` §3.** The Designer's dedicated
> "Shell Command Mode" / interactive terminal was removed as part of the
> Designer slim-down (the Designer owns script authoring and testing only).
> The related Jython-facing `system.python3.execShell` scripting function
> had already been removed earlier as a shell-injection sink (review item
> C16, see `SECURITY.md`). Run shell commands via `subprocess.run([...])`
> from Python Code Mode as shown above, or from a terminal on the Gateway
> host.

---

## Available Python Modules

> **Note:** the "safe" vs "full access" split below is a leftover from the
> module-whitelist filter removed in v4.0.0. There is no module-level
> distinction any more — every module (including everything in "Designer
> Full Access Modules" below) is available to any authenticated
> Designer/Administrator caller. The split is kept here only as a
> discovery aid (commonly used modules vs. system/network modules).

### Commonly Used Modules

These modules cover most everyday scripting needs:

**Standard Library:**
- `math`, `json`, `datetime`, `time`, `calendar`
- `re` (regular expressions), `random`, `uuid`
- `itertools`, `collections`, `functools`, `operator`
- `decimal`, `fractions`, `statistics`
- `hashlib`, `base64`, `hmac`
- `string`, `textwrap`, `difflib`
- `enum`, `copy`, `pickle`

**Example:**
```python
import math
import json
import datetime

result = math.sqrt(16)
data = json.dumps({'result': result})
now = datetime.datetime.now()

print(f"Result: {result}")
print(f"JSON: {data}")
print(f"Time: {now}")
```

### Designer Full Access Modules

As a Designer user, you have **DESIGNER_ADMIN** mode, which grants full access to:

**System Modules:**
- `os` - Operating system interface
- `sys` - System-specific parameters
- `subprocess` - Subprocess management
- `pathlib` - Object-oriented filesystem paths
- `shutil` - High-level file operations

**Network Modules:**
- `socket` - Low-level networking
- `urllib` - URL handling
- `http` - HTTP library
- `requests` - HTTP for humans (if installed)

**Data Processing:**
- `pandas` - Data analysis (if installed)
- `numpy` - Numerical computing (if installed)
- `csv` - CSV file reading/writing
- `xml` - XML processing
- `sqlite3` - SQLite database

**Web & APIs:**
- `requests` - HTTP requests (pip install requests)
- `beautifulsoup4` - HTML parsing (pip install beautifulsoup4)
- `lxml` - XML/HTML processing (pip install lxml)

**Example - Full Access:**
```python
# System access (Designer only)
import os
import subprocess
import requests

# Get system info
print(f"OS: {os.name}")
print(f"Current dir: {os.getcwd()}")

# Run shell command
result = subprocess.run(['echo', 'Hello'], capture_output=True)
print(f"Command output: {result.stdout.decode()}")

# HTTP request
response = requests.get('https://api.github.com')
print(f"GitHub API status: {response.status_code}")
```

### Installing New Modules

Package and Python-version management is owned by the **Gateway web UI**
(Config → Python 3 Integration → Packages), operated by a Gateway
administrator — see `docs/operations/PACKAGE_MANAGEMENT.md` and
`docs/PROJECT_CHARTER.md` §3. The Designer's previous Packages dialog and
version-manager dialog were removed as part of the Designer slim-down; the
Designer now shows the environment (installed versions/packages)
**read-only** so you know what you can `import`.

If `import pandas` fails with `ModuleNotFoundError`, ask your Gateway
administrator to install it via the Gateway web UI (or via the REST API for
automation — see `docs/api/REST_API.md`). To check what's installed from a
script:

```python
import subprocess

# List installed packages
result = subprocess.run(['pip', 'list'], capture_output=True, text=True)
print(result.stdout)
```

### Modules Always Blocked

> **Removed in v4.0.0 — see `SECURITY.md` and `docs/PROJECT_CHARTER.md` §2.**
> Earlier versions blocked `ctypes`, `multiprocessing`, `threading`,
> `telnetlib`, and `paramiko` even for Designer users via an AST-based
> filter. That filter was removed because it was trivially bypassable and
> misrepresented the module's security guarantees. **No module is blocked**
> for an authenticated Designer/Administrator caller — `ctypes`,
> `multiprocessing`, `threading`, `telnetlib`, and `paramiko` all import and
> run normally. The real boundary is who holds the Designer/Administrator
> role and OS-level isolation of the Gateway host, not an in-process module
> filter.

---

## Best Practices

### 1. Use Descriptive Names

**Good:**
```python
# Script Name: Import Customer Data
# Description: Imports customer data from CSV and validates formats

import pandas as pd

def import_customer_data(file_path):
    df = pd.read_csv(file_path)
    # Validation logic...
    return df
```

**Bad:**
```python
# Script Name: Script1
# Description:

# Code with no comments...
```

### 2. Add Error Handling

Always wrap risky operations in try/except:

```python
import pandas as pd

try:
    df = pd.read_csv('data.csv')
    print(f"Loaded {len(df)} rows")
except FileNotFoundError:
    print("Error: File not found!")
except pd.errors.EmptyDataError:
    print("Error: File is empty!")
except Exception as e:
    print(f"Unexpected error: {e}")
```

### 3. Use Logging for Production Scripts

```python
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

logger = logging.getLogger(__name__)

# Use in code
logger.info("Script started")
logger.warning("Missing optional parameter")
logger.error("Failed to connect to database")
```

### 4. Test with Small Data First

```python
# Read only first 10 rows for testing
df = pd.read_csv('large_file.csv', nrows=10)
print(df.head())

# Once verified, read full file
# df = pd.read_csv('large_file.csv')
```

### 5. Clean Up Resources

```python
# Use context managers for file operations
with open('data.txt', 'r') as f:
    data = f.read()
    # File automatically closed

# Or explicit cleanup
file = open('data.txt', 'r')
try:
    data = file.read()
finally:
    file.close()  # Always closes, even on error
```

### 6. Document Your Code

```python
def calculate_average(numbers):
    """
    Calculate the average of a list of numbers.

    Args:
        numbers (list): List of numeric values

    Returns:
        float: Average of the numbers

    Raises:
        ValueError: If list is empty

    Example:
        >>> calculate_average([1, 2, 3, 4, 5])
        3.0
    """
    if not numbers:
        raise ValueError("Cannot calculate average of empty list")

    return sum(numbers) / len(numbers)
```

### 7. Use Type Hints (Python 3)

```python
from typing import List, Dict, Optional

def process_data(data: List[Dict], threshold: float = 0.5) -> Optional[Dict]:
    """
    Process data and return results above threshold.
    """
    results = [d for d in data if d.get('value', 0) > threshold]
    return results[0] if results else None
```

---

## Troubleshooting

### Problem: "Failed to connect to Gateway"

**Symptoms:**
Cannot execute code, connection error message

**Solutions:**
1. **Check Gateway URL** - Ensure correct URL (e.g., `http://localhost:8088`)
2. **Verify Gateway is Running** - Open Gateway webpage in browser
3. **Check Firewall** - Ensure port 8088 is not blocked
4. **Try HTTPS** - Some Gateways require `https://` instead of `http://`

**Test Connection:**
```python
# In Script Console (Jython), test connection
import urllib2
response = urllib2.urlopen('http://localhost:8088/StatusPing')
print response.read()
```

### Problem: "Module not found"

**Symptoms:**
`ModuleNotFoundError: No module named 'pandas'`

**Solutions:**
1. **Ask a Gateway administrator to install the package** via the Gateway
   web UI (Config → Python 3 Integration → Packages) — the Designer has no
   package-install surface (see `docs/PROJECT_CHARTER.md` §3)

2. **Check installation**:
   ```python
   import subprocess
   result = subprocess.run(['pip', 'list'], capture_output=True, text=True)
   print(result.stdout)
   ```

3. **Verify Python path**:
   ```python
   import sys
   print(sys.path)
   ```

### Problem: "SecurityException — authentication required"

**Symptoms:**
`403 Forbidden — authentication required`

**Explanation:**
This should NOT happen in the Designer IDE — an authenticated Designer
session always gets DESIGNER_ADMIN capability. As of v4.2.0, the Designer's
core script authoring/exec/eval path runs over the platform's authenticated
module-RPC channel (`GatewayConnection.getRpcInterface`), gated by
`ClientReqSession.isDesigner()` on the Gateway side — not by a REST session
token — so this error on that path means the Designer's own Ignition
session is not recognised as a Designer session. A few secondary read-only
panels (completions, diagnostics, distribution info) still use the older
REST `/auth/session` bearer-token path and can hit this if the Ignition
session lacks the `Designer`/`Administrator` role.

> **What changed in v4.0.0:** The previous `RESTRICTED` mode and its module-whitelist error message (`Module 'os' not allowed in RESTRICTED mode`) were removed. All authenticated callers have full Python capabilities.

**Solutions:**
1. **Verify your Ignition user has the Designer or Administrator role**
2. **Check Gateway Logs** - Look for RPC session/role lookup errors
   (`Python3RpcHandler`) or, for the legacy REST panels,
   `/auth/session` errors
3. **Restart Designer** - Close and reopen Python 3 IDE to re-establish the
   Gateway connection
4. **Contact Administrator** - If issue persists, your Designer session's
   role membership may be misconfigured on the Gateway

### Problem: Code runs slowly

**Symptoms:**
Long execution times, timeouts

**Solutions:**
1. **Check Pool Stats** - Look at Diagnostics panel:
   - If all processes in use: increase pool size
   - If processes unhealthy: restart Gateway

2. **Optimize Code**:
   ```python
   # Slow: Reading large file line by line
   with open('large.txt', 'r') as f:
       for line in f:
           process(line)

   # Faster: Read in chunks
   with open('large.txt', 'r') as f:
       while True:
           lines = f.readlines(10000)
           if not lines:
               break
           for line in lines:
               process(line)
   ```

3. **Use pandas efficiently**:
   ```python
   # Slow: Apply function to each row
   df['result'] = df.apply(lambda row: complex_function(row), axis=1)

   # Faster: Vectorized operations
   df['result'] = df['column1'] * df['column2']
   ```

### Problem: "Script not saving"

**Symptoms:**
Script save fails, no error message

**Solutions:**
1. **Check script name** - Must not be empty
2. **Check Gateway disk space** - Ensure Gateway has free space
3. **Check permissions** - Gateway must have write access to data directory
4. **Try different name** - Avoid special characters

### Problem: Output not showing

**Symptoms:**
Code executes but no output appears

**Solutions:**
1. **Use print() statements**:
   ```python
   # Won't show in output
   result = 2 + 2

   # Will show in output
   result = 2 + 2
   print(f"Result: {result}")
   ```

2. **Check Error panel** - Error may have occurred silently

3. **Return value explicitly**:
   ```python
   # Last expression returned
   2 + 2  # This value appears in output
   ```

---

## Keyboard Shortcuts

### Editor Shortcuts

| Action | Windows/Linux | Mac |
|--------|--------------|-----|
| Execute Code | `Ctrl+Enter` | `Cmd+Return` |
| Save Script | `Ctrl+S` | `Cmd+S` |
| New Script | `Ctrl+N` | `Cmd+N` |
| Find | `Ctrl+F` | `Cmd+F` |
| Replace | `Ctrl+H` | `Cmd+H` |
| Comment Line | `Ctrl+/` | `Cmd+/` |
| Indent | `Tab` | `Tab` |
| Un-indent | `Shift+Tab` | `Shift+Tab` |
| Undo | `Ctrl+Z` | `Cmd+Z` |
| Redo | `Ctrl+Y` | `Cmd+Shift+Z` |

### Code Completion

- `Ctrl+Space` - Trigger autocomplete (if available)
- `Tab` - Accept completion
- `Esc` - Cancel completion

### Navigation

- `Ctrl+Home` - Go to start of document
- `Ctrl+End` - Go to end of document
- `Ctrl+Left/Right` - Jump by word
- `Ctrl+G` - Go to line

---

## Tips & Tricks

### 1. Quick Testing with Comments

```python
# Uncomment to test different scenarios
# data = load_test_data()   # Small test dataset
data = load_production_data()  # Full dataset
```

### 2. Use f-strings for Formatting

```python
# Old way
print("Name: " + name + ", Age: " + str(age))

# New way (Python 3)
print(f"Name: {name}, Age: {age}")
```

### 3. Quick Data Inspection

```python
import pandas as pd

df = pd.read_csv('data.csv')

# Quick overview
print(df.info())        # Column types and counts
print(df.describe())    # Statistical summary
print(df.head(10))      # First 10 rows
print(df.tail(10))      # Last 10 rows
print(df.sample(5))     # Random 5 rows
```

### 4. Benchmark Execution Time

```python
import time

start = time.time()

# Your code here
result = complex_calculation()

duration = time.time() - start
print(f"Execution time: {duration:.2f} seconds")
```

### 5. Debug with Rich Output

```python
import json

# Pretty-print dictionaries
data = {'name': 'Alice', 'age': 25}
print(json.dumps(data, indent=2))
```

---

## Need Help?

### Resources

- **Module Documentation**: See `SECURITY_GUIDE.md` for technical details
- **Python 3 Tutorial**: https://docs.python.org/3/tutorial/
- **Pandas Documentation**: https://pandas.pydata.org/docs/
- **Ignition Forum**: https://forum.inductiveautomation.com/

### Support

For module issues or questions:
- GitHub Issues: https://github.com/Gaskony-Ignition/ignition-module-python3/issues
- Ignition Forum: Module Development section

---

**Happy Coding! 🐍**

*This guide was created for Python 3 Integration v2.6.0 - Last updated October 2025*
