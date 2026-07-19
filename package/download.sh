#!/usr/bin/env bash
set -euo pipefail

ARCH_FILTER=""
DIR_FILTER=""

usage() {
  cat <<EOF
用法: $(basename "$0") [--arch <x86_64|aarch64|common>] [--dir <raw|base|docker|helm|yum|apt>]

  --arch    仅下载匹配该架构的包（arch=common 的条目与架构无关，始终包含）
  --dir     仅下载路由到该目的目录的包（对应 manifest 条目的 repoType；
            未显式声明 repoType 时按扩展名推断，见脚本内 infer_repo_type）
  -h, --help  显示本帮助

不带参数时下载 manifest.json 中的全部公有包（原有行为不变）。

示例:
  $(basename "$0")                          # 全量下载（原有行为）
  $(basename "$0") --dir base               # 只下载 CLI 基础设施 bundle（nexus/mysql/rustfs）
  $(basename "$0") --arch x86_64 --dir base # 只下载 x86_64 架构的基础设施 bundle
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --arch)
      ARCH_FILTER="${2:?--arch 需要一个值}"
      shift 2
      ;;
    --dir)
      DIR_FILTER="${2:?--dir 需要一个值}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: 未知参数 $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

MANIFEST="$(cd "$(dirname "$0")" && pwd)/manifest.json"
PKG_DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v curl &>/dev/null; then
  echo "ERROR: 需要 curl"
  exit 1
fi

python3 - "$MANIFEST" "$PKG_DIR" "$ARCH_FILTER" "$DIR_FILTER" <<'PYEOF'
import hashlib, json, os, subprocess, sys

manifest_path, pkg_dir, arch_filter, dir_filter = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

with open(manifest_path) as f:
    pkgs = json.load(f)

VALID_DIRS = {"yum", "apt", "helm", "docker", "base", "raw"}
if dir_filter and dir_filter not in VALID_DIRS:
    print(f"ERROR: --dir 取值必须是 {sorted(VALID_DIRS)} 之一，收到 {dir_filter!r}", file=sys.stderr)
    sys.exit(1)

# 目录结构与 upload/registry.go repositoryUploadBatch 对齐：
#   yum/<arch>/<os>/*.rpm
#   apt/<arch>/<os>/*.deb
#   raw/packages/*
#   helm/*.tgz
#   docker/*.tar
#   base/*                 CLI 自身基础设施 bundle（nexus/mysql/rustfs 等），
#                           对应 cluster-sample.yml 的 packages: 段，不经 Nexus 服务安装流程
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

def repo_type_for(pkg):
    return pkg.get("repoType") or infer_repo_type(pkg["packageName"])

def dest_dir_for(pkg):
    arch     = pkg.get("arch", "common")
    rtype    = repo_type_for(pkg)
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
    if rtype == "base":
        return os.path.join(pkg_dir, "base")
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

if arch_filter or dir_filter:
    print(f"[FILTER] arch={arch_filter or '(all)'}  dir={dir_filter or '(all)'}")

seen = set()
skipped_private = []
download_errors = []
skipped_by_filter = 0

for pkg in pkgs:
    name = pkg["packageName"]
    url = pkg.get("downloadUrl")
    service = pkg["service"]
    arch = pkg["arch"]

    if arch_filter and arch != arch_filter and arch != "common":
        skipped_by_filter += 1
        continue
    if dir_filter and repo_type_for(pkg) != dir_filter:
        skipped_by_filter += 1
        continue

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
if skipped_by_filter:
    print(f"\n[已按 --arch/--dir 过滤跳过 {skipped_by_filter} 条]")
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
