#!/usr/bin/env bash

set -euo pipefail

VALKEY_VERSION="8.1.8"
EXPORTER_VERSION="1.84.0"
PACKAGE_DIR="valkey-${VALKEY_VERSION}-openeuler22.03-x86_64"
VALKEY_SHA256="0edc455ba7524f0cfa4f73fdc70b91dec6941e893a09bcbdd012470d08043cec"
EXPORTER_SHA256="f13280147f1a0f6ed5f5d61ac80620c0b64049d76a99c7a5f043319efeb368fd"

usage() {
  echo "Usage: $0 <valkey-source.tar.gz> <redis_exporter-linux-amd64.tar.gz> [output-dir]"
}

if [[ $# -lt 2 || $# -gt 3 ]]; then
  usage
  exit 1
fi

valkey_source=$(realpath "$1")
exporter_archive=$(realpath "$2")
output_dir=$(realpath -m "${3:-.}")

if [[ $(uname -m) != "x86_64" ]]; then
  echo "This package must be built on x86_64." >&2
  exit 1
fi

if [[ ! -r /etc/os-release ]]; then
  echo "Cannot detect the operating system: /etc/os-release is missing." >&2
  exit 1
fi

# shellcheck disable=SC1091
source /etc/os-release
if [[ ${ID:-} != "openEuler" || ${VERSION_ID:-} != 22.03* ]]; then
  echo "This package must be built on openEuler 22.03; detected ${PRETTY_NAME:-unknown}." >&2
  exit 1
fi

for command in gcc make tar gzip sha256sum strip; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing build command: $command" >&2
    exit 1
  fi
done

printf '%s  %s\n' "$VALKEY_SHA256" "$valkey_source" | sha256sum -c -
printf '%s  %s\n' "$EXPORTER_SHA256" "$exporter_archive" | sha256sum -c -

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/valkey-openeuler-build.XXXXXX")
trap 'rm -rf "$work_dir"' EXIT

mkdir -p "$work_dir/source" "$work_dir/root/$PACKAGE_DIR/redis-exporter" "$output_dir"
tar -xzf "$valkey_source" -C "$work_dir/source"
tar -xzf "$exporter_archive" -C "$work_dir/source"

valkey_dir="$work_dir/source/valkey-${VALKEY_VERSION}"
exporter_dir="$work_dir/source/redis_exporter-v${EXPORTER_VERSION}.linux-amd64"
package_root="$work_dir/root/$PACKAGE_DIR"

make -C "$valkey_dir" -j"$(getconf _NPROCESSORS_ONLN)" BUILD_TLS=yes
make -C "$valkey_dir" PREFIX="$package_root" install
strip "$package_root/bin/valkey-server" \
  "$package_root/bin/valkey-cli" \
  "$package_root/bin/valkey-benchmark"

install -m 0755 "$exporter_dir/redis_exporter" "$package_root/redis-exporter/redis_exporter"
install -m 0644 "$valkey_dir/COPYING" "$package_root/VALKEY-LICENSE"
install -m 0644 "$exporter_dir/LICENSE" "$package_root/REDIS-EXPORTER-LICENSE"

{
  printf 'Valkey version: %s\n' "$VALKEY_VERSION"
  printf 'redis_exporter version: %s\n' "$EXPORTER_VERSION"
  printf 'Build OS: %s\n' "${PRETTY_NAME}"
  printf 'Build architecture: %s\n' "$(uname -m)"
  printf 'TLS: OpenSSL %s\n' "$(openssl version | sed 's/^OpenSSL //')"
  printf 'Valkey source SHA-256: %s\n' "$VALKEY_SHA256"
  printf 'redis_exporter SHA-256: %s\n' "$EXPORTER_SHA256"
} > "$package_root/BUILD-INFO"

artifact="$output_dir/$PACKAGE_DIR.tar.gz"
tar --sort=name \
  --mtime='UTC 2026-06-02 00:00:00' \
  --owner=0 --group=0 --numeric-owner \
  -C "$work_dir/root" -cf - "$PACKAGE_DIR" | gzip -n > "$artifact"

sha256sum "$artifact"
