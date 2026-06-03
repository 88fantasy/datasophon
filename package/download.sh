#!/usr/bin/env bash
set -euo pipefail

MANIFEST="$(cd "$(dirname "$0")" && pwd)/manifest.json"
PKG_DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v curl &>/dev/null; then
  echo "ERROR: 需要 curl"
  exit 1
fi

python3 - "$MANIFEST" "$PKG_DIR" <<'PYEOF'
import hashlib, json, os, subprocess, sys

manifest_path, pkg_dir = sys.argv[1], sys.argv[2]

with open(manifest_path) as f:
    pkgs = json.load(f)

# 目录结构与 upload/registry.go repositoryUploadBatch 对齐：
#   yum/<arch>/<os>/*.rpm
#   apt/<arch>/<os>/*.deb
#   raw/packages/*
#   helm/*.tgz
#   docker/*.tar
# manifest 条目可声明 "repoType" 和 "os" 字段覆盖自动推断；
# "os" 缺省为 "common"。
def infer_repo_type(name):
    if name.endswith(".rpm"):
        return "yum"
    if name.endswith(".deb"):
        return "apt"
    if name.endswith(".tar"):
        return "docker"
    # .tgz 与 .tar.gz 语义相同，默认 raw；Helm chart 须在 manifest 显式声明 repoType=helm
    return "raw"

def dest_dir_for(pkg):
    name     = pkg["packageName"]
    arch     = pkg.get("arch", "common")
    rtype    = pkg.get("repoType") or infer_repo_type(name)
    if rtype == "yum":
        os_name = pkg.get("os", "common")
        return os.path.join(pkg_dir, "yum", arch, os_name)
    if rtype == "apt":
        os_name = pkg.get("os", "common")
        return os.path.join(pkg_dir, "apt", arch, os_name)
    if rtype == "helm":
        return os.path.join(pkg_dir, "helm")
    if rtype == "docker":
        return os.path.join(pkg_dir, "docker")
    return os.path.join(pkg_dir, "raw", "packages")

def compute_md5(path):
    h = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

def write_md5(dest):
    md5 = compute_md5(dest)
    with open(dest + ".md5", "w") as f:
        f.write(md5)
    return md5

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

    d = dest_dir_for(pkg)
    os.makedirs(d, exist_ok=True)
    dest = os.path.join(d, name)

    if os.path.exists(dest):
        local_size = os.path.getsize(dest)
        md5_file = dest + ".md5"
        if os.path.exists(md5_file):
            # 优先用 MD5 校验文件完整性
            with open(md5_file) as f:
                stored_md5 = f.read().strip()
            actual_md5 = compute_md5(dest)
            if actual_md5 == stored_md5:
                print(f"[EXISTS] {name}  ({local_size / 1024 / 1024:.1f} MB)  md5=OK")
                continue
            print(f"[MD5-MISMATCH] {name}  (期望 {stored_md5[:8]}… 实际 {actual_md5[:8]}…) → 重新下载")
            os.remove(dest)
            os.remove(md5_file)
        else:
            # 无 MD5 文件，降级用 HEAD Content-Length 判断
            try:
                head = subprocess.run(
                    ["curl", "-sI", "--max-time", "15", "--location", url],
                    capture_output=True, text=True
                )
                if head.returncode != 0:
                    print(f"[EXISTS?] {name}  ({local_size / 1024 / 1024:.1f} MB, HEAD 失败 exit={head.returncode}) → 跳过")
                    continue
                content_length = None
                for line in head.stdout.splitlines():
                    if line.lower().startswith("content-length:"):
                        content_length = int(line.split(":", 1)[1].strip())
                if content_length is None or content_length == 0:
                    # 服务端未返回有效 Content-Length（chunked 或重定向中间值），保守跳过
                    print(f"[EXISTS?] {name}  ({local_size / 1024 / 1024:.1f} MB, 无 Content-Length) → 跳过")
                    continue
                if local_size >= content_length:
                    md5 = write_md5(dest)
                    print(f"[EXISTS] {name}  ({local_size / 1024 / 1024:.1f} MB)  md5={md5}")
                    continue
                remote_mb = content_length / 1024 / 1024
                print(f"[PARTIAL] {name}  (本地 {local_size / 1024 / 1024:.1f} MB / 远端 {remote_mb:.1f} MB) → 重新下载")
                os.remove(dest)
            except Exception:
                print(f"[EXISTS?] {name}  ({local_size / 1024 / 1024:.1f} MB, 无法验证大小) → 跳过")
                continue

    rel_dest = os.path.relpath(dest, pkg_dir)
    print(f"[DOWNLOAD] {service}/{arch} → {rel_dest}")
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
            md5 = write_md5(dest)
            print(f"  [DONE]  {rel_dest}  ({size_mb:.1f} MB)  md5={md5}")
    except Exception as e:
        download_errors.append(f"  [ERROR] {name}: {e}")

print("\n===== 下载完成 =====")
if skipped_private:
    print("\n[私有包，需手动上传到对应子目录]")
    for m in skipped_private:
        print(m)
if download_errors:
    print("\n[下载失败]")
    for m in download_errors:
        print(m)
    sys.exit(1)
PYEOF
