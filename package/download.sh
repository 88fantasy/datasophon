#!/usr/bin/env bash
set -euo pipefail

MANIFEST="$(cd "$(dirname "$0")" && pwd)/manifest.json"
PKG_DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v curl &>/dev/null; then
  echo "ERROR: 需要 curl"
  exit 1
fi

python3 - "$MANIFEST" "$PKG_DIR" <<'PYEOF'
import json, os, subprocess, sys

manifest_path, pkg_dir = sys.argv[1], sys.argv[2]

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

    # 用 HEAD 请求获取 Content-Length，与本地文件对比，跳过完整文件
    if os.path.exists(dest):
        local_size = os.path.getsize(dest)
        try:
            head = subprocess.run(
                ["curl", "-sI", "--max-time", "15", "--location", url],
                capture_output=True, text=True
            )
            if head.returncode != 0:
                # HEAD 请求失败，保守跳过，不删除已有文件
                print(f"[EXISTS?] {name}  ({local_size / 1024 / 1024:.1f} MB, HEAD 失败 exit={head.returncode}) → 跳过")
                continue
            content_length = None
            for line in head.stdout.splitlines():
                if line.lower().startswith("content-length:"):
                    content_length = int(line.split(":", 1)[1].strip())
            if content_length is None or content_length == 0:
                # 服务端未返回有效 Content-Length（chunked 或重定向中间值），无法比对，保守跳过
                print(f"[EXISTS?] {name}  ({local_size / 1024 / 1024:.1f} MB, 无 Content-Length) → 跳过")
                continue
            if local_size >= content_length:
                print(f"[EXISTS] {name}  ({local_size / 1024 / 1024:.1f} MB)")
                continue
            remote_mb = content_length / 1024 / 1024
            print(f"[PARTIAL] {name}  (本地 {local_size / 1024 / 1024:.1f} MB / 远端 {remote_mb:.1f} MB) → 重新下载")
            os.remove(dest)
        except Exception:
            print(f"[EXISTS?] {name}  ({local_size / 1024 / 1024:.1f} MB, 无法验证大小) → 跳过")
            continue

    print(f"[DOWNLOAD] {service}/{arch} → {name}")
    print(f"           {url}")
    try:
        cmd = ["curl", "-L", "--max-time", "1800", "--retry", "3",
               "--progress-bar", "-o", dest, url]
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
