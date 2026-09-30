#!/usr/bin/env python3
"""
Helper script to download Python wheels for bundling in the module.

This script downloads platform-specific wheels for Windows x64 and Linux x64
based on the packages.json configuration.

Usage:
    python download_wheels.py

Requirements:
    - Python 3.8+
    - pip

The script will create platform-specific subdirectories and download wheels from PyPI.
"""

import json
import subprocess
import sys
from pathlib import Path


def main():
    """Download all wheels specified in packages.json"""
    script_dir = Path(__file__).parent
    packages_json = script_dir.parent / "packages.json"

    # Load package configuration
    with open(packages_json) as f:
        packages = json.load(f)

    print("=" * 80)
    print("Python Wheel Downloader for Ignition Python3 Integration Module")
    print("=" * 80)
    print()

    # Download for each platform
    platforms = {
        "windows-x64": ("win_amd64", "cp311"),
        "linux-x64": ("manylinux", "cp311")
    }

    for platform_dir, (platform_tag, python_tag) in platforms.items():
        print(f"\nDownloading wheels for {platform_dir}...")
        print("-" * 80)

        dest_dir = script_dir / platform_dir
        dest_dir.mkdir(exist_ok=True)

        # Track downloaded packages to avoid duplicates
        downloaded = set()

        for package_name, package_info in packages.items():
            print(f"\n📦 Package: {package_name}")
            print(f"   Description: {package_info['description']}")
            print(f"   Size: {package_info['sizeMb']} MB")

            for wheel in package_info["wheels"]:
                # Handle platform placeholders
                if "{platform}" in wheel:
                    # Get actual wheel name from PyPI
                    pip_package = wheel.split("-")[0]
                    if pip_package in downloaded:
                        continue
                    downloaded.add(pip_package)

                    print(f"   Downloading: {pip_package} (platform-specific)")
                    download_wheel_pip(pip_package, dest_dir, platform_tag, python_tag)
                else:
                    # Pure Python wheel
                    pip_package = wheel.split("-")[0]
                    if pip_package in downloaded:
                        continue
                    downloaded.add(pip_package)

                    print(f"   Downloading: {pip_package}")
                    download_wheel_pip(pip_package, dest_dir, None, None)

        print(f"\n✅ {platform_dir} wheels downloaded to: {dest_dir}")

    print("\n" + "=" * 80)
    print("✅ All wheels downloaded successfully!")
    print("=" * 80)
    print("\nNext steps:")
    print("1. Review downloaded wheels in windows-x64/ and linux-x64/")
    print("2. Update packages.json 'wheels' arrays with actual filenames if needed")
    print("3. Rebuild the module to include the bundled wheels")
    print()


def download_wheel_pip(package_name, dest_dir, platform_tag=None, python_tag=None):
    """
    Download a wheel using pip download.

    Args:
        package_name: Package name (e.g., 'numpy')
        dest_dir: Destination directory
        platform_tag: Platform tag (e.g., 'win_amd64', 'manylinux')
        python_tag: Python version tag (e.g., 'cp311')
    """
    cmd = [
        sys.executable,
        "-m", "pip", "download",
        "--no-deps",  # Don't download dependencies (we specify them explicitly)
        "--dest", str(dest_dir),
        "--python-version", "3.11"
    ]

    if platform_tag and python_tag:
        # Platform-specific wheel
        cmd.extend([
            "--platform", platform_tag,
            "--implementation", "cp",
            "--abi", python_tag,
            "--only-binary", ":all:"  # Only download wheels, not source
        ])

    cmd.append(package_name)

    try:
        subprocess.run(cmd, check=True, capture_output=True)
    except subprocess.CalledProcessError as e:
        print(f"   ⚠️  Warning: Failed to download {package_name}")
        print(f"      Error: {e.stderr.decode()}")
        print(f"      You may need to download this manually from PyPI")


def list_wheel_files():
    """List all downloaded wheel files for verification."""
    script_dir = Path(__file__).parent

    print("\n" + "=" * 80)
    print("Downloaded Wheels")
    print("=" * 80)

    for platform_dir in ["windows-x64", "linux-x64"]:
        wheel_dir = script_dir / platform_dir
        if not wheel_dir.exists():
            continue

        wheels = list(wheel_dir.glob("*.whl"))
        total_size = sum(w.stat().st_size for w in wheels) / (1024 * 1024)

        print(f"\n{platform_dir}: {len(wheels)} wheels ({total_size:.1f} MB)")
        for wheel in sorted(wheels):
            size_mb = wheel.stat().st_size / (1024 * 1024)
            print(f"  - {wheel.name} ({size_mb:.2f} MB)")


if __name__ == "__main__":
    try:
        main()
        list_wheel_files()
    except KeyboardInterrupt:
        print("\n\n⚠️  Download interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
