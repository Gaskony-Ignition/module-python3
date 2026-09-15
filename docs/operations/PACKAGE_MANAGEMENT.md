# Package Management Guide

**Module:** Python 3 Integration for Ignition 8.3+

Complete guide for installing and managing Python packages with the module.

---

## Overview

Package management is owned by the **Gateway web UI**
(`docs/PROJECT_CHARTER.md` §3) — a Gateway administrator installs and
removes packages there; Designer developers see the resulting environment
read-only. The supported methods:

1. **Gateway Web UI Packages manager** - the administrator surface of record
2. **REST API** - For automation
3. **Bundled packages** - For air-gapped deployments

---

## Method 1: Designer IDE (Shell Command Mode)

> **Removed — see `docs/PROJECT_CHARTER.md` §3.** The Designer's Shell
> Command Mode / interactive terminal was removed as part of the Designer
> slim-down; the Designer no longer offers any package-install surface.
> Use the Gateway web UI (Method 2 below was also removed — see that note)
> or the REST API instead. A Designer developer who needs a package should
> ask a Gateway administrator to install it.

---

## Method 2: Packages Dialog (GUI)

> **Removed — see `docs/PROJECT_CHARTER.md` §3.** The Designer's Packages
> dialog (PyPI search, install, uninstall) was removed along with the rest
> of the Designer's write-capable environment management. The equivalent
> functionality lives in the **Gateway web UI → Config → Python 3
> Integration → Packages**, where an administrator can:
>
> - Search PyPI and view package details
> - Install a package (optionally pinned, e.g. `package==1.2.3`)
> - View installed packages (name, version, location)
> - Uninstall packages
>
> The Designer shows the installed environment read-only.

---

## Method 3: REST API

**For automation and scripting.** All routes require authentication
(Administrator/Designer session token or admin API key — see
`docs/api/REST_API.md`). The package name goes **in the URL path**
(URL-encoded), not in a JSON body.

### Install Package

Tries the bundled-wheel catalogue first, then falls back to PyPI:

```bash
curl -X POST \
  "http://localhost:8088/data/python3integration/api/v1/packages/install/requests" \
  -H "Authorization: Bearer <api-key>"
```

### Uninstall Package

```bash
curl -X POST \
  "http://localhost:8088/data/python3integration/api/v1/packages/uninstall/requests" \
  -H "Authorization: Bearer <api-key>"
```

### List Installed Packages / Bundle Status

```bash
curl -H "Authorization: Bearer <api-key>" \
  "http://localhost:8088/data/python3integration/api/v1/packages/status"
```

### View Bundled-Package Catalogue

```bash
curl -H "Authorization: Bearer <api-key>" \
  "http://localhost:8088/data/python3integration/api/v1/packages/catalog"
```

### Search PyPI

Query goes in the `q` parameter (GET, not POST):

```bash
curl -H "Authorization: Bearer <api-key>" \
  "http://localhost:8088/data/python3integration/api/v1/packages/search-pypi?q=requests"
```

### PyPI Package Details

```bash
curl -H "Authorization: Bearer <api-key>" \
  "http://localhost:8088/data/python3integration/api/v1/packages/pypi-info/requests"
```

---

## Common Packages

> The `pip install ...` commands below are for a Gateway administrator
> working in a terminal **on the Gateway host** (using the same Python the
> module runs — check `system.python3.getDistributionInfo()` for the path).
> Alternatively, enter just the package name in the Gateway web UI's
> Packages manager, or use the REST install route above.

### Web & API

```bash
# HTTP requests
pip install requests

# Web scraping
pip install beautifulsoup4 lxml

# JSON/XML processing
pip install xmltodict
```

### Data Science

```bash
# Core data science stack
pip install numpy pandas matplotlib

# Scientific computing
pip install scipy scikit-learn

# Jupyter notebooks
pip install jupyter notebook
```

### Database

```bash
# PostgreSQL
pip install psycopg2-binary

# MySQL
pip install mysql-connector-python

# SQLite (built-in, no install needed)
import sqlite3

# SQLAlchemy ORM
pip install sqlalchemy
```

### Utilities

```bash
# Date/time handling
pip install python-dateutil pytz

# File operations
pip install pathlib2

# Configuration
pip install python-dotenv pyyaml
```

---

## Package Installation Locations

### With Virtual Environment

Packages install to venv:
```
/path/to/venv/lib/python3.X/site-packages/
```

### Without Virtual Environment

Packages install to bundled Python:
```
<gateway-data>/python3-integration/python/lib/python3.X/site-packages/
```

Check location:
```bash
python -c "import site; print(site.getsitepackages())"
```

---

## Requirements Files

### Create requirements.txt

**Export current packages**:
```bash
pip freeze > requirements.txt
```

**Example requirements.txt**:
```
requests==2.31.0
pandas==2.0.3
numpy==1.24.3
matplotlib==3.7.2
```

### Install from requirements.txt

```bash
pip install -r requirements.txt
```

### Version Pinning

```bash
# Exact version
requests==2.31.0

# Minimum version
requests>=2.30.0

# Compatible version
requests~=2.31.0  # >=2.31.0, <2.32.0

# Any version
requests
```

---

## Air-Gapped Deployments

See [AIR_GAPPED_DEPLOYMENT.md](AIR_GAPPED_DEPLOYMENT.md) for bundling packages into the module.

---

## Troubleshooting

### Installation Fails

**externally-managed-environment error**:
```bash
# Use --break-system-packages flag
pip install requests --break-system-packages
```

**Permission denied**:
- Check Gateway user has write access to site-packages
- On Linux, may need to run Gateway as different user
- Consider using virtual environment

**Package not found on PyPI**:
- Check spelling and capitalization
- Search PyPI website: https://pypi.org/
- Verify package name (sometimes different from import name)

### Import Errors After Installation

**ModuleNotFoundError: No module named 'package'**:
- Verify package installed: `pip list`
- Check Python path: `sys.path`
- Restart Gateway to reload sys.path
- Check virtual environment is active

**Version conflicts**:
- Check installed versions: `pip list`
- Upgrade conflicting packages
- Use version ranges in requirements.txt

### Slow Installation

**Large packages (numpy, pandas, scipy)**:
- Progress shown in the Gateway web UI's Packages manager output
- May take 1-5 minutes depending on package size
- Check Gateway CPU usage during install
- Consider pre-compiled wheels for your platform

---

## Best Practices

1. **Use requirements.txt** - Track dependencies in version control
2. **Pin versions** - Ensure reproducible deployments
3. **Test before production** - Verify packages work in test environment
4. **Use virtual environments** - Avoid dependency conflicts
5. **Regular updates** - Keep packages updated for security fixes
6. **Minimize dependencies** - Only install what you need
7. **Review security** - Check packages for known vulnerabilities

---

## Package Security

### Check Package Safety

```bash
# Install safety checker
pip install safety

# Scan for known vulnerabilities
safety check
```

### Verify Package Integrity

```bash
# Check package hash
pip hash requests

# Verify signature (if available)
pip verify requests
```

### Best Practices

- Only install from trusted sources (PyPI)
- Review package maintainers and download stats
- Check package homepage and documentation
- Scan for vulnerabilities before production use
- Keep packages updated for security patches

---

## Common Workflows

### Workflow 1: Add New Package

```bash
# 1. Install package
pip install requests

# 2. Test in script
import requests
response = requests.get('https://api.example.com')

# 3. Update requirements.txt
pip freeze > requirements.txt

# 4. Commit to version control
git add requirements.txt
git commit -m "Add requests package"
```

### Workflow 2: Update All Packages

```bash
# 1. List outdated packages
pip list --outdated

# 2. Update specific package
pip install --upgrade requests

# 3. Or update all (careful!)
pip list --outdated | cut -d ' ' -f1 | xargs pip install -U

# 4. Update requirements.txt
pip freeze > requirements.txt
```

### Workflow 3: Clean Install

```bash
# 1. Export current packages
pip freeze > requirements-backup.txt

# 2. Uninstall all packages
pip freeze | xargs pip uninstall -y

# 3. Install from requirements
pip install -r requirements.txt

# 4. Verify
pip list
```

---

## Integration with Ignition

### Using Packages in Scripts

```python
# In Ignition Script Console or Designer IDE
import requests

# Make API call
response = requests.get('https://api.example.com/data')
data = response.json()

# Process data
for item in data:
    print(item['name'])
```

### Using Packages in Gateway Events

```python
# Gateway Event Script
import pandas as pd

# Read CSV from Gateway
df = pd.read_csv('/path/to/data.csv')

# Process data
summary = df.describe()
print(summary)
```

---

**Need help?** See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) or open an issue on GitHub.
