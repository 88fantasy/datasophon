#!/usr/bin/env bash
set -euo pipefail

ARCH_FILTER=""
DIR_FILTER=""
PRINT_LAYOUT="false"

usage() {
  cat <<EOF
用法: $(basename "$0") [--arch <x86_64|aarch64|common>] [--dir <raw|base|docker|helm|yum|apt>] [--print-layout]

  --arch    仅下载匹配该架构的包（arch=common 的条目与架构无关，始终包含）
  --dir     仅下载路由到该目的目录的包（对应 manifest 条目的 repoType/repoTypes；
            未显式声明时按扩展名推断，见脚本内 infer_repo_type）
  --print-layout  只打印 manifest 的目标路径，不下载
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
    --print-layout)
      PRINT_LAYOUT="true"
      shift
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

if [[ "$PRINT_LAYOUT" != "true" ]] && ! command -v curl &>/dev/null; then
  echo "ERROR: 需要 curl"
  exit 1
fi

python3 - "$MANIFEST" "$PKG_DIR" "$ARCH_FILTER" "$DIR_FILTER" "$PRINT_LAYOUT" <<'PYEOF'
import hashlib, json, os, shutil, subprocess, sys

manifest_path, pkg_dir, arch_filter, dir_filter, print_layout_arg = sys.argv[1:6]
print_layout = print_layout_arg == "true"

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
# manifest 条目可声明 "repoType" 或 "repoTypes" 和 "os" 字段覆盖自动推断；
# repoTypes 用于同一制品同时服务 CLI base 与 Nexus raw 等多个消费方。
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

def repo_types_for(pkg):
    values = pkg.get("repoTypes")
    if values is None:
        values = [pkg.get("repoType") or infer_repo_type(pkg["packageName"])]
    if not isinstance(values, list) or not values:
        raise ValueError(f"{pkg['packageName']} 的 repoTypes 必须是非空数组")
    result = []
    for value in values:
        if value not in VALID_DIRS:
            raise ValueError(f"{pkg['packageName']} 的 repoTypes 包含非法值 {value!r}")
        if value not in result:
            result.append(value)
    return result

def dest_dir_for(pkg, rtype):
    arch     = pkg.get("arch", "common")
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

def selected_repo_types(pkg):
    values = repo_types_for(pkg)
    if dir_filter:
        return [dir_filter] if dir_filter in values else []
    return values

if print_layout:
    for pkg in pkgs:
        arch = pkg.get("arch", "common")
        if arch_filter and arch != arch_filter and arch != "common":
            continue
        repo_types = selected_repo_types(pkg)
        if not repo_types:
            continue
        destinations = [os.path.relpath(os.path.join(dest_dir_for(pkg, value), pkg["packageName"]), pkg_dir)
                        for value in repo_types]
        print(f"{pkg['packageName']} -> {','.join(destinations)}")
    sys.exit(0)

def existing_is_valid(dest, url, name):
    if not os.path.exists(dest):
        return False
    local_size = os.path.getsize(dest)
    md5_file = dest + ".md5"
    if os.path.exists(md5_file):
        with open(md5_file) as f:
            stored_md5 = f.read().strip()
        actual_md5 = compute_md5(dest)
        if actual_md5 == stored_md5:
            print(f"[EXISTS] {os.path.relpath(dest, pkg_dir)}  ({local_size / 1024 / 1024:.1f} MB)  md5=OK")
            return True
        print(f"[MD5-MISMATCH] {name}  (期望 {stored_md5[:8]}… 实际 {actual_md5[:8]}…) → 重新下载")
        os.remove(dest)
        os.remove(md5_file)
        return False
    try:
        head = subprocess.run(
            ["curl", "-sI", "--max-time", "15", "--location", url],
            capture_output=True, text=True
        )
        if head.returncode != 0:
            print(f"[EXISTS?] {name}  ({local_size / 1024 / 1024:.1f} MB, HEAD 失败 exit={head.returncode}) → 跳过")
            return True
        content_length = None
        for line in head.stdout.splitlines():
            if line.lower().startswith("content-length:"):
                content_length = int(line.split(":", 1)[1].strip())
        if content_length is None or content_length == 0:
            print(f"[EXISTS?] {name}  ({local_size / 1024 / 1024:.1f} MB, 无 Content-Length) → 跳过")
            return True
        if local_size >= content_length:
            md5 = write_md5(dest)
            print(f"[EXISTS] {os.path.relpath(dest, pkg_dir)}  ({local_size / 1024 / 1024:.1f} MB)  md5={md5}")
            return True
        print(f"[PARTIAL] {name}  (本地 {local_size / 1024 / 1024:.1f} MB / 远端 {content_length / 1024 / 1024:.1f} MB) → 重新下载")
        os.remove(dest)
        return False
    except Exception:
        print(f"[EXISTS?] {name}  ({local_size / 1024 / 1024:.1f} MB, 无法验证大小) → 跳过")
        return True

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
    repo_types = selected_repo_types(pkg)
    if not repo_types:
        skipped_by_filter += 1
        continue

    if name in seen:
        continue
    seen.add(name)

    if not url:
        skipped_private.append(f"  [SKIP-PRIVATE] {name}  # {pkg.get('note', '')}")
        continue

    destinations = [os.path.join(dest_dir_for(pkg, value), name) for value in repo_types]
    for dest in destinations:
        os.makedirs(os.path.dirname(dest), exist_ok=True)
    valid_destinations = [dest for dest in destinations if existing_is_valid(dest, url, name)]
    source = valid_destinations[0] if valid_destinations else None

    if source is None:
        source = destinations[0]
        rel_source = os.path.relpath(source, pkg_dir)
        print(f"[DOWNLOAD] {service}/{arch} → {rel_source}")
        print(f"           {url}")
        try:
            result = subprocess.run(["curl", "-L", "--max-time", "1800", "--retry", "3",
                                     "--progress-bar", "-o", source, url])
            if result.returncode != 0:
                download_errors.append(f"  [ERROR] {name}: 下载失败，exit={result.returncode}")
                if os.path.exists(source) and os.path.getsize(source) == 0:
                    os.remove(source)
                continue
            size_mb = os.path.getsize(source) / 1024 / 1024
            md5 = write_md5(source)
            print(f"  [DONE]  {rel_source}  ({size_mb:.1f} MB)  md5={md5}")
        except Exception as e:
            download_errors.append(f"  [ERROR] {name}: {e}")
            continue

    for dest in destinations:
        if dest == source or dest in valid_destinations:
            continue
        try:
            if os.path.exists(dest):
                os.remove(dest)
            if os.path.exists(dest + ".md5"):
                os.remove(dest + ".md5")
            try:
                os.link(source, dest)
            except OSError:
                shutil.copy2(source, dest)
            md5 = write_md5(dest)
            print(f"  [MIRROR] {os.path.relpath(source, pkg_dir)} → {os.path.relpath(dest, pkg_dir)}  md5={md5}")
        except Exception as e:
            download_errors.append(f"  [ERROR] {name}: 镜像到 {os.path.relpath(dest, pkg_dir)} 失败: {e}")

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
