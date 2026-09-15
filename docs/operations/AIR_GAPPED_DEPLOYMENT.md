# Air-Gapped Deployment Guide

**Module:** Python 3 Integration for Ignition 8.3+

Complete guide for deploying the Python 3 Integration module in air-gapped/offline environments.

---

## Overview

The module includes a package bundling system that allows you to pre-install Python packages into the .modl file for offline deployment scenarios.

---

## When to Bundle Packages

Bundle packages when:
- **Air-gapped networks**: Gateway has no internet access
- **Corporate environments**: Restricted PyPI access or proxy issues
- **Reproducible deployments**: Guarantee exact package versions
- **Security requirements**: Package verification before deployment

**Note:** If your Gateway has internet access, install packages via the
**Gateway web UI's Packages manager** (Config → Python 3 Integration →
Packages) after module installation, or via the REST API for automation
(see `docs/operations/PACKAGE_MANAGEMENT.md`). Package management is a
Gateway-administrator function; the Designer no longer has a
package-install surface (`docs/PROJECT_CHARTER.md` §3).

---

## Default Bundled Packages

**✅ Jedi (v0.19.2) - Always Bundled**
- **Size**: ~1.6 MB
- **Purpose**: IDE autocomplete functionality
- **Auto-installed**: Yes, on Gateway startup
- **Required for**: Designer IDE autocomplete

Verify installation in Gateway logs (`wrapper.log`):
```
INFO [Python3PackageManager] Jedi already installed - autocomplete ready
```

---

## Optional Package Bundles

**🌐 Web Package Bundle**
- **Size**: ~0.6 MB
- **Includes**: requests, urllib3, certifi, charset-normalizer, idna
- **Use cases**: HTTP requests, REST API calls, web scraping

**📊 Data Science Bundle**
- **Size**: ~85 MB (Windows), ~60 MB (Linux)
- **Includes**: numpy, pandas, matplotlib + dependencies
- **Use cases**: Numerical computing, data analysis, plotting
- **Recommendation**: ⚠️ Install via `pip install` if internet available (large size)

---

## How to Bundle Additional Packages

### Step 1: Navigate to Python Packages Directory
```bash
cd gateway/src/main/resources/python-packages
```

### Step 2: Download Package Wheels
```bash
python3 download_wheels.py
```

Downloads platform-specific wheels for:
- Windows x64
- Linux x64

**Output**:
```
✅ windows-x64 wheels downloaded to: ./windows-x64
✅ linux-x64 wheels downloaded to: ./linux-x64
```

### Step 3: Verify Downloaded Wheels
```bash
ls windows-x64/*.whl
ls linux-x64/*.whl
```

### Step 4: Rebuild Module
```bash
cd ../../../..  # Back to repo root
./gradlew clean build --no-daemon
```

**Module Size Impact**:
- Base module: ~1.2 MB
- With jedi: ~2.8 MB
- With jedi + web: ~3.4 MB
- With jedi + web + datascience: ~88 MB (Windows), ~63 MB (Linux)

### Step 5: Install Module
Install the newly built `.modl` file - all bundled wheels are included.

---

## Adding Custom Packages

### 1. Edit packages.json
Add your package to `gateway/src/main/resources/packages.json`:

```json
{
  "mypackage": {
    "version": "1.0.0",
    "description": "My custom package",
    "sizeMb": 2.0,
    "wheels": [
      "mypackage-1.0.0-py3-none-any.whl"
    ],
    "pipPackages": ["mypackage"],
    "importName": "mypackage",
    "requiredFor": ["Custom functionality"]
  }
}
```

**Wheel filename formats**:
- Pure Python: `package-1.0.0-py3-none-any.whl`
- Platform-specific: `package-1.0.0-cp311-cp311-win_amd64.whl`
- Use placeholder: `package-1.0.0-cp311-cp311-{platform}.whl`

### 2. Download Wheels
```bash
cd gateway/src/main/resources/python-packages
python3 download_wheels.py
```

### 3. Rebuild and Install
```bash
cd ../../../../..
./gradlew clean build --no-daemon
```

---

## Installing Bundled Packages

### Automatic Installation (Jedi Only)
Jedi installs automatically on module startup.

### Manual Installation (Other Packages)

**Via the Gateway web UI**: open Config → Python 3 Integration → Packages
and install the `web` or `datascience` bundle from the catalogue.

**Via REST API** (authenticated — bundle name in the URL path; wheels come
from the local catalogue, falling back to PyPI only if not bundled):

```bash
curl -X POST -H "Authorization: Bearer <api-key>" \
  http://localhost:8088/data/python3integration/api/v1/packages/install/web
curl -X POST -H "Authorization: Bearer <api-key>" \
  http://localhost:8088/data/python3integration/api/v1/packages/install/datascience
```

Bundled wheels install from local files - no internet required.

---

## Platform Support

**Bundled Platforms**:
- ✅ Windows x64 (win_amd64)
- ✅ Linux x64 (manylinux)

**Not Bundled**:
- ❌ macOS - install via `pip install` after module installation

**Why not macOS?**
macOS wheels are large. Administrators can install packages via the Gateway
web UI's Packages manager (or `pip` on the Gateway host) after module
installation instead of bundling.

---

## Troubleshooting

**Wheel download fails**:
- Check internet connection
- Verify Python 3.8+ installed
- Install pip: `python3 -m ensurepip`
- Manual download: https://pypi.org/project/package-name/#files

**Package installation fails**:
- Check Gateway logs: `<ignition-install>/logs/wrapper.log`
- Verify wheel architecture matches Gateway OS
- Ensure bundled Python is used (not system Python)

**Wrong package version bundled**:
- Update version in `packages.json`
- Re-run `download_wheels.py`
- Verify `.whl` files in platform directories
- Rebuild module

---

## Best Practices

1. **Bundle only essentials** - Keep module size reasonable
2. **Test before deployment** - Verify packages work on target platform
3. **Document dependencies** - Update `packages.json` description
4. **Version lock** - Specify exact versions for reproducibility
5. **Security scan** - Review packages before bundling

---

## Technical Details

**Package Storage**:
- Wheels: `gateway/src/main/resources/python-packages/{platform}/`
- Catalog: `gateway/src/main/resources/packages.json`

**Installation Process**:
1. Module extracts wheels to: `<gateway-data>/python3-integration/packages/`
2. Gateway runs: `pip install --no-index --find-links packages/ package-name`
3. Packages install to bundled Python's site-packages
4. Tracking: `<gateway-data>/python3-integration/installed-packages.json`

**Auto-Installation (Jedi)**:
- Triggered in `GatewayHook.startup()` (line 85-100)
- Only runs if jedi not already installed
- Uses `Python3PackageManager.installPackage("jedi")`

---

**Need help?** See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) or open an issue on GitHub.
