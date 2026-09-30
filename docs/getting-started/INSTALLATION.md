# Installation Guide - Python 3 Integration Module

**Target Platform:** Ignition 8.3.0+

This guide covers installation, upgrading, and verification of the Python 3 Integration module.

---

## 📋 Prerequisites

### Ignition Gateway
- **Version:** Ignition 8.3.0 or later
- **Java:** JDK 17+ (included with Ignition 8.3)
- **Memory:** Minimum 2GB RAM, 4GB+ recommended
- **Disk Space:** 500MB for module + Python packages

### Python (Optional - Module includes bundled Python)
- **Recommended:** Python 3.9, 3.11, or 3.12
- **Note:** Module includes bundled Python for Windows/Linux
- **macOS Users:** Install Python separately or use bundled Python

---

## 🚀 Standard Installation

### Step 1: Download Module

Download the latest `.modl` file:
- **GitHub Releases:** https://github.com/Gaskony-Ignition/ignition-module-python3/releases
- **Build from Source:** See [Development Setup](#development-setup)

### Step 2: Install in Gateway

1. Open Ignition Gateway web interface (http://localhost:8088)
2. Login with administrator credentials
3. Navigate to: **Config → System → Modules**
4. Click **Install or Upgrade a Module**
5. Browse and select the `.modl` file
6. Click **Install**
7. Gateway will restart automatically

### Step 3: Verify Installation

Check module status:
1. Go to: **Config → System → Modules**
2. Look for **"Python 3 Integration"**
3. Status should show: **✓ Running**

Test in Script Console:
```python
# Test Python 3 execution
result = system.python3.exec("result = 2 + 2")
print(result)  # Should print: 4

# Check Python version
version = system.python3.getVersion()
print(version)  # e.g., "3.11.5"
```

### Step 4: Open Designer IDE (Optional)

1. Open Ignition Designer
2. Go to: **Tools → Python 3 IDE**
3. Connect to Gateway
4. Write Python code and click **Execute**

---

## 🔄 Upgrading from Previous Version

### Upgrade Process

1. **Backup your scripts** (recommended):
   ```bash
   # Scripts stored in: <gateway-data>/python3/scripts/
   cp -r <gateway-data>/python3/scripts/ ~/python3-backup/
   ```

2. **Install new version** using same steps as Standard Installation
   - Gateway will detect existing module and upgrade
   - **One restart required**

3. **Verify upgrade:**
   ```python
   # Check version
   system.python3.getVersion()
   ```

### Upgrade Notes

- **Scripts preserved:** All saved scripts remain intact
- **Settings preserved:** Gateway configuration retained
- **Packages preserved:** Installed Python packages remain
- **Breaking changes:** See [CHANGELOG.md](../../CHANGELOG.md)

---

## 🛠️ Development Setup

### Building from Source

1. **Clone repository:**
   ```bash
   git clone https://github.com/Gaskony-Ignition/ignition-module-python3.git
   cd ignition-module-python3/python3-integration
   ```

2. **Build module:**
   ```bash
   ./gradlew clean build --no-daemon
   ```

3. **Locate .modl file:**
   ```bash
   ls -lh build/libs/*.modl
   # Python3-2.15.10-signed.modl
   ```

4. **Install in Gateway** using Standard Installation steps

---

## 🐳 Docker Installation

### Using Docker Compose

```yaml
version: '3'
services:
  ignition:
    image: inductiveautomation/ignition:8.3
    ports:
      - "8088:8088"
    volumes:
      - ./python3-integration-signed.modl:/modules/python3.modl
      - ignition-data:/usr/local/bin/ignition/data
volumes:
  ignition-data:
```

Start container:
```bash
docker-compose up -d
```

Module auto-installs on first startup.

---

## ☁️ Cloud Deployment

### AWS / Azure / GCP

1. **Deploy Ignition Gateway** using cloud marketplace or custom AMI/image
2. **Upload .modl file** to cloud storage (S3, Blob Storage, Cloud Storage)
3. **Install module** via Gateway web interface
4. **Configure networking:**
   - Gateway: Port 8088 (HTTP) or 8043 (HTTPS)
   - Designer: Port 8060 (GAN)

### Security Considerations

- Enable HTTPS for Gateway web interface
- Use API keys for REST API access
- Configure firewall rules (ports 8088, 8043, 8060)
- See [docs/security/CONFIG.md](../security/CONFIG.md)

---

## 🔒 Production Configuration

### Recommended Settings

**Gateway Configuration** (`ignition.conf`):
```properties
# Python path (optional - uses bundled Python by default)
wrapper.java.additional.101=-Dignition.python3.path=/usr/bin/python3.11

# Process pool size (default: 3)
wrapper.java.additional.102=-Dignition.python3.poolsize=5

# Enable security mode
wrapper.java.additional.103=-Dignition.python3.security=DESIGNER_ADMIN
```

### Virtual Environment Setup (v2.12.0+)

```bash
# Create venv
python3 -m venv /path/to/myproject/venv

# Set in module settings or gateway config
VIRTUAL_ENV=/path/to/myproject/venv
```

---

## ❌ Uninstallation

### Remove Module

1. Go to: **Config → System → Modules**
2. Find **"Python 3 Integration"**
3. Click **Uninstall**
4. Gateway restarts

### Clean Up Data (Optional)

```bash
# Remove saved scripts
rm -rf <gateway-data>/python3/scripts/

# Remove Python packages (if not using system Python)
rm -rf <gateway-data>/python3/packages/
```

---

## 🔧 Troubleshooting

### Module Won't Install

**Error:** "Module signature invalid"
- **Solution:** Use signed `.modl` file from `build/libs/`
- **Cause:** Using unsigned development build

**Error:** "Incompatible Ignition version"
- **Solution:** Upgrade to Ignition 8.3.0+
- **Check:** Config → System → About

### Module Installed But Not Running

1. **Check logs:** `<ignition-install>/logs/wrapper.log`
2. **Search for:** `Python3` or `ERROR`
3. **Common issues:**
   - Python path not found
   - Permissions issue
   - Port conflict

See [TROUBLESHOOTING.md](../operations/TROUBLESHOOTING.md) for complete guide.

---

## 📚 Next Steps

After installation:
1. **Quick Start:** [QUICK_START.md](QUICK_START.md)
2. **Keyboard Shortcuts:** [KEYBOARD_SHORTCUTS.md](KEYBOARD_SHORTCUTS.md)
3. **Package Management:** [PACKAGE_MANAGEMENT.md](../operations/PACKAGE_MANAGEMENT.md)
4. **Security Configuration:** [docs/security/CONFIG.md](../security/CONFIG.md)

---

## 📞 Support

- **Documentation:** [docs/README.md](../README.md)
- **Issues:** https://github.com/Gaskony-Ignition/ignition-module-python3/issues
- **Changelog:** [CHANGELOG.md](../../CHANGELOG.md)

---

