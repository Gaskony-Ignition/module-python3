# Python Package Bundling

This directory contains Python wheel (.whl) files that are bundled with the module for offline installation.

## Structure

```
python-packages/
├── windows-x64/          # Windows 64-bit wheels
│   ├── jedi-*.whl
│   ├── requests-*.whl
│   ├── numpy-*.whl
│   └── ...
├── linux-x64/            # Linux 64-bit wheels
│   ├── jedi-*.whl
│   ├── requests-*.whl
│   ├── numpy-*.whl
│   └── ...
├── packages.json         # Package metadata (in parent directory)
├── download_wheels.py    # Helper script to download wheels
└── README.md             # This file
```

## Downloading Wheels

### Option 1: Automated Download (Recommended)

Run the helper script to download all required wheels:

```bash
cd gateway/src/main/resources/python-packages
python3 download_wheels.py
```

This will download all wheels specified in `packages.json` for both Windows and Linux platforms.

### Option 2: Manual Download

Download wheels manually from PyPI:

```bash
# Create destination directories
mkdir -p windows-x64 linux-x64

# Download for Windows (example: jedi)
pip download --no-deps --dest windows-x64 --platform win_amd64 --python-version 3.11 --only-binary :all: jedi

# Download for Linux (example: jedi)
pip download --no-deps --dest linux-x64 --platform manylinux2014_x86_64 --python-version 3.11 --only-binary :all: jedi
```

## Package Bundles

The module provides three package bundles:

### 1. **jedi** (~3MB per platform)
- Intelligent auto-completion for IDE
- Pure Python, works on all platforms
- **Recommended for all users**

Packages:
- jedi
- parso

### 2. **web** (~5MB per platform)
- HTTP library for web/API calls
- Pure Python, works on all platforms
- **Recommended for users making HTTP requests**

Packages:
- requests
- urllib3
- certifi
- charset-normalizer
- idna

### 3. **datascience** (~85MB per platform)
- Numerical computing and data analysis
- Platform-specific (contains compiled extensions)
- **For users doing data analysis, plotting**

Packages:
- numpy
- pandas
- matplotlib
- python-dateutil, pytz, six, tzdata
- contourpy, cycler, fonttools, kiwisolver
- packaging, pillow, pyparsing

## Adding New Packages

To add a new package bundle:

1. **Update packages.json** (in parent directory):
```json
{
  "mypackage": {
    "version": "1.0.0",
    "description": "My custom package",
    "sizeMb": 5.0,
    "wheels": [
      "mypackage-1.0.0-py3-none-any.whl"
    ],
    "pipPackages": [
      "mypackage"
    ],
    "importName": "mypackage",
    "requiredFor": [
      "Custom functionality"
    ]
  }
}
```

2. **Download wheels** for both platforms:
```bash
python3 download_wheels.py
```

3. **Rebuild module** to include the new wheels

## Size Considerations

- **With Jedi only**: +6MB (3MB × 2 platforms)
- **With Web tools**: +16MB (8MB × 2)
- **With Data Science**: +206MB (103MB × 2)

The .modl file size will increase by the total size of bundled wheels.

## Installation in Air-Gapped Environments

Once wheels are bundled:

1. Install module as normal (.modl file contains all wheels)
2. In Gateway config page, select which packages to install
3. Click "Install Selected Packages"
4. Packages install from bundled wheels (no internet required)

## Verification

After downloading wheels, verify they're correct:

```bash
# List all wheels
find windows-x64 linux-x64 -name "*.whl" | sort

# Check total size
du -sh windows-x64 linux-x64
```

## Notes

- Wheels are platform and Python-version specific
- This module targets Python 3.11
- Pure Python wheels (py3-none-any) work on all platforms
- Platform wheels (cp311-cp311-*) are platform-specific
- Always test package installation after bundling

## Troubleshooting

**Wheel download fails:**
- Check internet connection
- Try downloading manually from https://pypi.org/
- Some packages may not have pre-built wheels for all platforms

**Wrong wheel downloaded:**
- Verify Python version (should be 3.11)
- Check platform tags (win_amd64, manylinux2014_x86_64)
- Manually download correct wheel if needed

**Module size too large:**
- Bundle only essential packages (jedi + web)
- Data science packages are optional
- Users can install packages manually if needed
