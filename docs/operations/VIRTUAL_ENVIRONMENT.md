# Virtual Environment Support

**Module:** Python 3 Integration for Ignition 8.3+

Guide for using Python virtual environments (venv) with the module.

---

## Overview

As of v2.12.0, the module fully supports Python virtual environments with:
- Automatic venv detection
- VIRTUAL_ENV environment variable propagation
- UI status display showing active environment
- Proper package isolation

---

## What is a Virtual Environment?

A virtual environment is an isolated Python environment that allows you to:
- Install packages without affecting system Python
- Use different package versions per project
- Avoid dependency conflicts
- Maintain reproducible environments

---

## Creating a Virtual Environment

### Option 1: Using Designer IDE (Shell Command Mode)

> **Removed — see `docs/PROJECT_CHARTER.md` §3.** The Designer's Shell
> Command Mode was removed as part of the Designer slim-down, and it was
> never the right tool here anyway: `source .../activate` only modifies
> the current interactive shell and has no effect inside a Gateway
> subprocess. Create the venv from a terminal on the Gateway host
> (Option 2 below), then point the module at it via
> `-Dignition.python3.venv` or the Python path override (see below).

### Option 2: Using Terminal

```bash
# Create venv
python3 -m venv ~/.python3ide/venv

# Activate
source ~/.python3ide/venv/bin/activate  # Linux/Mac
~/.python3ide/venv/Scripts/activate     # Windows
```

---

## Configuring Module to Use venv

### Method 1: venv System Property

Set in `ignition.conf` (the property is `ignition.python3.venv` — the
module then exports `VIRTUAL_ENV` into each Python subprocess for you):
```properties
wrapper.java.additional.X=-Dignition.python3.venv=/path/to/myenv
```

Restart Ignition Gateway.

### Method 2: Python Path

Point Python path to venv's Python:
```properties
wrapper.java.additional.X=-Dignition.python3.path=/path/to/myenv/bin/python3
```

---

## Automatic venv Detection

The module automatically detects if Python is running from a venv by:
1. Checking VIRTUAL_ENV environment variable
2. Checking sys.prefix vs sys.base_prefix
3. Looking for venv markers in Python path

When detected:
- ✅ VIRTUAL_ENV propagated to all Python subprocesses
- ✅ Packages installed to venv (isolated from system)
- ✅ Gateway web UI shows the active venv in its environment status

---

## Checking Active Environment

### Via a Script (Designer Script Console or any project script)

```python
info = system.python3.getDistributionInfo()
if info.get("usingVenv"):
    print("Virtual env: " + info["venvPath"])
else:
    print("Not using a virtual environment")
```

(The Designer's previous Packages dialog, which displayed the venv status,
was removed — environment management now lives in the Gateway web UI, which
shows the active venv in its environment status.)

### Via REST API

```bash
curl http://localhost:8088/data/python3integration/api/v1/diagnostics
```

Response includes:
```json
{
  "virtualEnvironment": "/path/to/myenv",
  "pythonVersion": "3.11.5"
}
```

### Via Gateway Logs

Check `wrapper.log` for:
```
INFO [PythonDistributionManager] Virtual environment detected: /path/to/myenv
INFO [Python3Executor] VIRTUAL_ENV=/path/to/myenv propagated to subprocess
```

---

## Installing Packages in venv

Once venv is configured, all package installations go to the venv —
whether installed via the Gateway web UI's Packages manager, the REST API,
or `pip` from a terminal on the Gateway host with the venv activated:

```bash
# On the Gateway host, with the venv activated
pip install requests pandas numpy

# Packages install to:
# /path/to/myenv/lib/python3.X/site-packages/
```

---

## Benefits of Using venv

1. **Isolation** - No conflicts with system Python packages
2. **Reproducibility** - Export requirements.txt, recreate exact environment
3. **Safety** - Mistakes don't affect system Python
4. **Flexibility** - Different environments for different projects
5. **Clean uninstall** - Just delete venv directory

---

## Common Workflows

### Workflow 1: Development Environment

```bash
# 1. Create venv
python3 -m venv ~/.python3ide/dev-venv

# 2. Configure module (ignition.conf)
wrapper.java.additional.X=-Dignition.python3.venv=/home/user/.python3ide/dev-venv

# 3. Restart Ignition

# 4. Install dev packages (on the Gateway host, venv activated —
#    or via the Gateway web UI's Packages manager)
pip install requests pandas pytest black
```

### Workflow 2: Multiple Environments

```bash
# Production venv
python3 -m venv ~/.python3ide/prod-venv

# Development venv
python3 -m venv ~/.python3ide/dev-venv

# Switch by changing ignition.conf and restarting
```

### Workflow 3: Requirements File

```bash
# Export current packages
pip freeze > requirements.txt

# Recreate environment elsewhere
python3 -m venv new-venv
source new-venv/bin/activate
pip install -r requirements.txt
```

---

## Troubleshooting

**Module not detecting venv**:
- Check VIRTUAL_ENV is set: `echo $VIRTUAL_ENV`
- Verify Python path points to venv: `which python3`
- Check Gateway logs for detection messages
- Restart Ignition after configuration changes

**Packages installing to system Python**:
- Verify venv is active (check UI status)
- Check VIRTUAL_ENV environment variable
- Ensure Python path points to venv's Python
- Gateway may need restart

**Permission denied errors**:
- Check venv directory permissions
- Ensure Gateway user has read/write access
- On Linux: `chmod -R 755 /path/to/venv`

**venv not found errors**:
- Verify venv path is absolute (not relative)
- Check venv directory exists
- Ensure Python binary exists at expected path

---

## Best Practices

1. **Use absolute paths** - Always use full paths for VIRTUAL_ENV
2. **Document requirements** - Keep requirements.txt updated
3. **One venv per project** - Don't share venvs across projects
4. **Regular updates** - Keep packages updated (`pip list --outdated`)
5. **Backup venvs** - Include requirements.txt in version control

---

## Technical Implementation

**venv Detection** (`PythonDistributionManager.java`):
- Checks `VIRTUAL_ENV` environment variable
- Compares `sys.prefix` vs `sys.base_prefix` in Python
- Scans Python path for venv indicators

**Environment Propagation** (`Python3Executor.java`):
```java
if (venvPath != null) {
    pb.environment().put("VIRTUAL_ENV", venvPath);
}
```

**UI Display** (Gateway web UI):
- The web UI queries the distribution status endpoint for venv status
- The Designer's previous `PackagesDialog` (which showed this) was removed
  — environment display now lives in the Gateway web UI, and scripts can
  query `system.python3.getDistributionInfo()`

---

## Migration from System Python

**To migrate from system Python to venv**:

1. **Export current packages**:
   ```bash
   pip freeze > requirements.txt
   ```

2. **Create venv**:
   ```bash
   python3 -m venv ~/.python3ide/venv
   ```

3. **Install packages**:
   ```bash
   source ~/.python3ide/venv/bin/activate
   pip install -r requirements.txt
   ```

4. **Configure module** (ignition.conf):
   ```properties
   wrapper.java.additional.X=-Dignition.python3.venv=/home/user/.python3ide/venv
   ```

5. **Restart Ignition**

6. **Verify**:
   - Check `system.python3.getDistributionInfo()` reports `usingVenv: True`
     (or check the Gateway web UI's environment status)
   - Test script execution
   - Verify packages available

---

**Need help?** See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) or open an issue on GitHub.
