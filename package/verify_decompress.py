#!/usr/bin/env python3
"""
验证各部署包的实际解压顶层目录是否与 service_ddl.json 中 decompressPackageName 一致。
不一致时自动写回修正。

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
    """
    try:
        if pkg_name.endswith(".tar.gz") or pkg_name.endswith(".tgz"):
            with tarfile.open(pkg_path, "r:*") as tf:
                members = tf.getmembers()
                # 含斜杠的条目才表示有顶层目录
                sub_paths = [m.name for m in members if "/" in m.name and not m.name.startswith(".")]
                if not sub_paths:
                    return None
                tops = Counter(p.split("/")[0] for p in sub_paths)
                return tops.most_common(1)[0][0]

        elif pkg_name.endswith(".zip"):
            with zipfile.ZipFile(pkg_path, "r") as zf:
                names = zf.namelist()
                sub_paths = [n for n in names if "/" in n and not n.startswith(".")]
                if not sub_paths:
                    return None
                tops = Counter(n.split("/")[0] for n in sub_paths)
                return tops.most_common(1)[0][0]

        else:
            return f"ERROR:不支持的格式 {pkg_name}"

    except Exception as e:
        return f"ERROR:{e}"


def find_ddl_paths_for_package(pkg_name):
    """找到所有 arch 中使用该 packageName 的 service_ddl.json 路径列表。"""
    results = []
    for service_dir in sorted(META_BASE.iterdir()):
        ddl = service_dir / "service_ddl.json"
        if not ddl.exists():
            continue
        with open(ddl) as f:
            data = json.load(f)
        arch = data.get("arch", {})
        for arch_val in arch.values():
            if arch_val.get("packageName") == pkg_name:
                results.append(ddl)
                break
    return results


def update_decompress_name(ddl_path, new_name):
    with open(ddl_path) as f:
        data = json.load(f)
    old = data.get("decompressPackageName")
    data["decompressPackageName"] = new_name
    with open(ddl_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"  [FIXED] {ddl_path.parent.name}/service_ddl.json: {old!r} → {new_name!r}")


def main():
    with open(MANIFEST_PATH) as f:
        manifest = json.load(f)

    # packageName → decompressPackageName（取第一次出现）
    seen = {}
    for entry in manifest:
        name = entry["packageName"]
        if name not in seen:
            seen[name] = entry["decompressPackageName"]

    ok = []
    mismatches = []
    missing = []
    binary_only = []
    errors = []

    for pkg_name, expected in seen.items():
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
        else:
            mismatches.append((pkg_name, expected, actual))
            print(f"[MISMATCH] {pkg_name}")
            print(f"           manifest.decompressPackageName = {expected!r}")
            print(f"           实际顶层目录                   = {actual!r}")
            for ddl_path in find_ddl_paths_for_package(pkg_name):
                update_decompress_name(ddl_path, actual)

    print("\n" + "=" * 50)
    print("校验汇总")
    print("=" * 50)
    print(f"  OK:          {len(ok)}")
    print(f"  MISMATCH:    {len(mismatches)} (已自动修正 service_ddl.json)")
    print(f"  BINARY-ONLY: {len(binary_only)} (跳过，单二进制包)")
    print(f"  MISSING:     {len(missing)} (未下载/私有包)")
    print(f"  ERROR:       {len(errors)}")

    if mismatches:
        print("\n修正清单：")
        for pkg, old, new in mismatches:
            print(f"  {pkg}")
            print(f"    {old!r} → {new!r}")
        print("\n请执行：git diff datasophon-api/src/main/resources/meta/datacluster/")

    if missing:
        print("\n未下载（私有包需手动上传）：")
        for m in missing:
            print(f"  {m}")


if __name__ == "__main__":
    main()
