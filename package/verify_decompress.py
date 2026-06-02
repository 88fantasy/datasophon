#!/usr/bin/env python3
"""
验证各部署包的实际解压顶层目录是否与 service_ddl.json 中 decompressPackageName 一致。
不一致时自动写回修正（service_ddl.json 和 manifest.json 同步更新）。

用法：python3 package/verify_decompress.py
"""
import json
import os
import tarfile
import zipfile
from collections import Counter
from pathlib import Path

BASE = Path(__file__).parent.parent
PKG_DIR = Path(__file__).parent
META_BASE = BASE / "datasophon-api/src/main/resources/meta/datacluster"
MANIFEST_PATH = PKG_DIR / "manifest.json"


def get_top_level_dir(pkg_path, pkg_name):
    """
    返回压缩包内唯一的顶层目录名。
    若包内无子目录（单二进制包）返回 None。
    出错时返回以 "ERROR:" 开头的字符串。
    流式读取前 20 个含子路径条目即可判断，避免对大包完整解压。
    """
    try:
        if pkg_name.endswith(".tar.gz") or pkg_name.endswith(".tgz"):
            with tarfile.open(pkg_path, "r:*") as tf:
                tops = Counter()
                for member in tf:
                    name = member.name
                    if "/" in name and not name.startswith("."):
                        tops[name.split("/")[0]] += 1
                        if sum(tops.values()) >= 20:
                            break
                if not tops:
                    return None
                return tops.most_common(1)[0][0]

        elif pkg_name.endswith(".zip"):
            with zipfile.ZipFile(pkg_path, "r") as zf:
                tops = Counter()
                for name in zf.namelist():
                    if "/" in name and not name.startswith("."):
                        tops[name.split("/")[0]] += 1
                        if sum(tops.values()) >= 20:
                            break
                if not tops:
                    return None
                return tops.most_common(1)[0][0]

        else:
            return f"ERROR:不支持的格式 {pkg_name}"

    except Exception as e:
        return f"ERROR:{e}"


def build_ddl_index():
    """预扫描所有 service_ddl.json，构建 packageName → [ddl_path] 索引，避免每次重扫。"""
    index = {}
    for service_dir in sorted(META_BASE.iterdir()):
        ddl = service_dir / "service_ddl.json"
        if not ddl.exists():
            continue
        with open(ddl) as f:
            data = json.load(f)
        for arch_val in data.get("arch", {}).values():
            pkg = arch_val.get("packageName")
            if pkg:
                index.setdefault(pkg, []).append(ddl)
                break
    return index


def update_decompress_name(ddl_path, new_name):
    """原子写入（写临时文件后 os.replace），避免写失败时 DDL 损坏。"""
    with open(ddl_path) as f:
        data = json.load(f)
    old = data.get("decompressPackageName")
    data["decompressPackageName"] = new_name
    tmp = ddl_path.parent / (ddl_path.name + ".tmp")
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        os.replace(tmp, ddl_path)
    except Exception:
        if tmp.exists():
            tmp.unlink()
        raise
    print(f"  [FIXED] {ddl_path.parent.name}/service_ddl.json: {old!r} → {new_name!r}")


def update_manifest_decompress_name(manifest_data, pkg_name, new_name):
    """将 manifest.json 中所有 pkg_name 条目的 decompressPackageName 更新为 new_name。"""
    changed = False
    for entry in manifest_data:
        if entry["packageName"] == pkg_name and entry.get("decompressPackageName") != new_name:
            entry["decompressPackageName"] = new_name
            changed = True
    return changed


def save_manifest(manifest_data):
    """原子写入 manifest.json。"""
    tmp = MANIFEST_PATH.parent / (MANIFEST_PATH.name + ".tmp")
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(manifest_data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        os.replace(tmp, MANIFEST_PATH)
    except Exception:
        if tmp.exists():
            tmp.unlink()
        raise


def main():
    with open(MANIFEST_PATH) as f:
        manifest = json.load(f)

    manifest_dirty = False

    # 构建 service → packageName 集合（用于多架构判断）
    service_packages = {}
    for entry in manifest:
        service_packages.setdefault(entry["service"], set()).add(entry["packageName"])

    # packageName → (decompressPackageName, is_multi_arch)
    # 同时记录 packageName → {service: decompressPackageName}，用于跨服务一致性检查
    seen = {}
    pkg_service_decompress = {}
    for entry in manifest:
        name = entry["packageName"]
        svc = entry["service"]
        decomp = entry["decompressPackageName"]
        is_multi_arch = len(service_packages[svc]) > 1
        pkg_service_decompress.setdefault(name, {})[svc] = decomp
        if name not in seen:
            seen[name] = (decomp, is_multi_arch)

    # 同一包被多个服务共用但 decompressPackageName 不一致时报警
    for pkg_name, svc_map in pkg_service_decompress.items():
        if len(set(svc_map.values())) > 1:
            print(f"[WARN] {pkg_name} 被多个服务共用但 decompressPackageName 不一致：{svc_map}")

    ddl_index = build_ddl_index()

    ok = []
    mismatches = []
    missing = []
    binary_only = []
    errors = []
    skipped_multi_arch = []

    for pkg_name, (expected, is_multi_arch) in seen.items():
        pkg_path = PKG_DIR / pkg_name
        if not pkg_path.exists():
            missing.append(pkg_name)
            continue

        actual = get_top_level_dir(pkg_path, pkg_name)

        if actual is None:
            binary_only.append(pkg_name)
            print(f"[BINARY-ONLY] {pkg_name} → 无顶层目录，跳过校验")
            continue

        if isinstance(actual, str) and actual.startswith("ERROR:"):
            errors.append(f"{pkg_name}: {actual}")
            print(f"[ERROR] {pkg_name}: {actual}")
            continue

        if actual == expected:
            ok.append(pkg_name)
            print(f"[OK]    {pkg_name}")
            print(f"        顶层目录: {actual!r}")
        elif is_multi_arch and actual.startswith(expected):
            # 多架构服务：实际目录 = 归一化名称 + 架构后缀，属预期，不修正
            skipped_multi_arch.append((pkg_name, expected, actual))
            print(f"[MULTI-ARCH] {pkg_name}")
            print(f"             decompressPackageName={expected!r}（归一化名称，保留）")
            print(f"             实际顶层目录         ={actual!r}（含架构后缀，符合预期）")
        else:
            mismatches.append((pkg_name, expected, actual))
            print(f"[MISMATCH] {pkg_name}")
            print(f"           manifest.decompressPackageName = {expected!r}")
            print(f"           实际顶层目录                   = {actual!r}")
            for ddl_path in ddl_index.get(pkg_name, []):
                update_decompress_name(ddl_path, actual)
            if update_manifest_decompress_name(manifest, pkg_name, actual):
                manifest_dirty = True

    if manifest_dirty:
        save_manifest(manifest)
        print("\n  [FIXED] manifest.json: decompressPackageName 已同步更新")

    print("\n" + "=" * 50)
    print("校验汇总")
    print("=" * 50)
    print(f"  OK:           {len(ok)}")
    print(f"  MISMATCH:     {len(mismatches)} (已自动修正 service_ddl.json + manifest.json)")
    print(f"  MULTI-ARCH:   {len(skipped_multi_arch)} (归一化名称，保留不修改)")
    print(f"  BINARY-ONLY:  {len(binary_only)} (跳过，单二进制包)")
    print(f"  MISSING:      {len(missing)} (未下载/私有包)")
    print(f"  ERROR:        {len(errors)}")

    if mismatches:
        print("\n修正清单：")
        for pkg, old, new in mismatches:
            print(f"  {pkg}")
            print(f"    {old!r} → {new!r}")
        print("\n请执行：git diff datasophon-api/src/main/resources/meta/datacluster/")

    if skipped_multi_arch:
        print("\n多架构包（归一化名称保留）：")
        for pkg, norm, actual in skipped_multi_arch:
            print(f"  {pkg}: decompressPackageName={norm!r} / 实际目录={actual!r}")

    if missing:
        print("\n未下载（私有包需手动上传）：")
        for m in missing:
            print(f"  {m}")


if __name__ == "__main__":
    main()
