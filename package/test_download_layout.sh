#!/usr/bin/env bash
# download.sh --print-layout 的离线自测（不联网、不下载）。用法: bash package/test_download_layout.sh
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAIL=0

check() { # <描述> <期望> <实际>
  if [[ "$2" == "$3" ]]; then
    echo "ok   $1"
  else
    echo "FAIL $1"; echo "  期望: $2"; echo "  实际: $3"; FAIL=1
  fi
}

# download.sh 按脚本所在目录找 manifest.json，所以复制到带临时 manifest 的目录中运行
layout() { # <manifest 内容>
  printf '%s' "$1" > "$TMP/manifest.json"
  bash "$TMP/download.sh" --print-layout 2>&1
}
cp "$HERE/download.sh" "$TMP/download.sh"

# ① 无 subDir 的条目：期望值为改动前脚本（dee22650）对同一 manifest 的实际输出
PLAIN='[
 {"packageName":"a.tar.gz","service":"A","arch":"common"},
 {"packageName":"b.rpm","service":"B","arch":"x86_64","os":"el7"},
 {"packageName":"c.tar","service":"C","arch":"aarch64"},
 {"packageName":"d.tgz","service":"D","arch":"common","repoType":"helm"},
 {"packageName":"e.tar.gz","service":"E","arch":"common","repoTypes":["base","raw"]}
]'
check "① 无 subDir 与改动前输出逐行一致" "a.tar.gz -> raw/packages/a.tar.gz
b.rpm -> yum/x86_64/el7/b.rpm
c.tar -> docker/c.tar
d.tgz -> helm/d.tgz
e.tar.gz -> base/e.tar.gz,raw/packages/e.tar.gz" "$(layout "$PLAIN")"

# ② subDir 只对 raw 生效
check "② subDir=plugins/x → raw/packages/plugins/x/<name>" \
  "p.jar -> raw/packages/plugins/x/p.jar" \
  "$(layout '[{"packageName":"p.jar","service":"S","arch":"common","subDir":"plugins/x"}]')"
check "② 多 repoTypes 时 subDir 不影响 base" \
  "q.jar -> base/q.jar,raw/packages/plugins/x/q.jar" \
  "$(layout '[{"packageName":"q.jar","service":"S","arch":"common","repoTypes":["base","raw"],"subDir":"plugins/x"}]')"

# ③ 非法 subDir 退出码非 0
for bad in '../evil' '/abs' 'plugins/../../evil' ''; do
  layout "[{\"packageName\":\"p.jar\",\"service\":\"S\",\"arch\":\"common\",\"subDir\":\"$bad\"}]" >/dev/null
  rc=$?
  if [[ $rc -ne 0 ]]; then echo "ok   ③ subDir='$bad' 退出码 $rc"; else echo "FAIL ③ subDir='$bad' 退出码 0"; FAIL=1; fi
done

[[ $FAIL -eq 0 ]] && echo "ALL PASS" || { echo "SOME FAILED"; exit 1; }
