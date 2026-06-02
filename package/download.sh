#!/usr/bin/env bash
set -euo pipefail

MANIFEST="$(cd "$(dirname "$0")" && pwd)/manifest.json"
PKG_DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v wget &>/dev/null && ! command -v curl &>/dev/null; then
  echo "ERROR: 需要 wget 或 curl"
  exit 1
fi

export DATASOPHON_MANIFEST="$MANIFEST"
export DATASOPHON_PKG_DIR="$PKG_DIR"

python3 - <<'PYEOF'
import json, os, subprocess, sys

manifest_path = os.environ["DATASOPHON_MANIFEST"]
pkg_dir = os.environ["DATASOPHON_PKG_DIR"]

with open(manifest_path) as f:
    pkgs = json.load(f)

seen = set()
skipped_private = []
download_errors = []

for pkg in pkgs:
    name = pkg["packageName"]
    url = pkg.get("downloadUrl")
    service = pkg["service"]
    arch = pkg["arch"]

    if name in seen:
        continue
    seen.add(name)

    if not url:
        skipped_private.append(f"  [SKIP-PRIVATE] {name}  # {pkg.get('note', '')}")
        continue

    dest = os.path.join(pkg_dir, name)
    if os.path.exists(dest):
        size_mb = os.path.getsize(dest) / 1024 / 1024
        print(f"[EXISTS] {name}  ({size_mb:.1f} MB)")
        continue

    print(f"[DOWNLOAD] {service}/{arch} → {name}")
    print(f"           {url}")
    try:
        has_wget = subprocess.run(["wget", "--version"], capture_output=True).returncode == 0
        if has_wget:
            cmd = ["wget", "--continue", "--timeout=1800", "--tries=3",
                   "--show-progress", "-O", dest, url]
        else:
            cmd = ["curl", "-L", "--continue-at", "-", "--max-time", "1800",
                   "--retry", "3", "--progress-bar", "-o", dest, url]
        result = subprocess.run(cmd)
        if result.returncode != 0:
            download_errors.append(f"  [ERROR] {name}: 下载失败，exit={result.returncode}")
            if os.path.exists(dest) and os.path.getsize(dest) == 0:
                os.remove(dest)
        else:
            size_mb = os.path.getsize(dest) / 1024 / 1024
            print(f"  [DONE]  {name}  ({size_mb:.1f} MB)")
    except Exception as e:
        download_errors.append(f"  [ERROR] {name}: {e}")

print("\n===== 下载完成 =====")
if skipped_private:
    print("\n[私有包，需手动上传到 package/]")
    for m in skipped_private:
        print(m)
if download_errors:
    print("\n[下载失败]")
    for m in download_errors:
        print(m)
    sys.exit(1)
PYEOF
