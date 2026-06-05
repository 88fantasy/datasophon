#!/bin/bash

# 脚本名称: juicefs-format-wrapper.sh
# 功能: 安全地执行 JuiceFS format 命令，避免重复初始化元数据引擎


# 显示帮助信息
show_help() {
    cat << EOF
Usage: $0 [OPTIONS] METAURL NAME

Wrapper script for juicefs format that checks if the metadata engine already exists.

Arguments:
  METAURL    Metadata engine URL (e.g., redis://localhost:6379/1)
  NAME       File system name

Options:
  -h, --help    Show this help message

Environment variables (same as juicefs format):
  --storage     Storage type (e.g., s3, oss)
  --bucket      Bucket URL
  --access-key  Access key for storage
  --secret-key  Secret key for storage
  --no-update   Disable automatic updates

Example:
  $0 --storage s3 --bucket mybucket.s3.amazonaws.com \\
     --access-key AKIAxxx --secret-key xxx \\
     --no-update redis://localhost:6379/1 myfs

Note: All juicefs format options are supported. See 'juicefs format --help' for details.
EOF
}

# 检查是否提供了帮助参数
if [[ $# -eq 0 ]] || [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
    show_help
    exit 0
fi

# 解析参数，找到 metaurl 和 name
# 我们假设 metaurl 和 name 是最后两个位置参数
args=("$@")
for ((i=0; i<${#args[@]}; i++)); do
    if [[ "${args[i]}" == "--" ]]; then
        break
    fi
done

# 获取最后两个参数作为 metaurl 和 name
metaurl="${args[-2]}"
name="${args[-1]}"

# 验证 metaurl 和 name 是否为空
if [[ -z "$metaurl" ]] || [[ -z "$name" ]]; then
    echo "Error: METAURL and NAME must be specified"
    exit 2
fi



SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JUICEFS_CMD="$SCRIPT_DIR/../juicefs"
# 检查 metaurl 是否存在
if "$JUICEFS_CMD" status "$metaurl" &>/dev/null; then
    
    # 获取文件系统列表，检查是否存在指定名称
    if "$JUICEFS_CMD" status "$metaurl" | grep -q "\"Name\": \"$name\""; then
        echo "File system '$name' already exists in metadata engine."
        exit 0
    else
        echo "Error: Metadata engine exists but does not contain file system '$name'."
        exit 1
    fi
else
    
    echo "Executing: $JUICEFS_CMD format ${args[@]}"
    # 执行 format 命令
    if "$JUICEFS_CMD" format "${args[@]}"; then
        echo "File system '$name' created successfully."
        exit 0
    else
        echo "Error: Failed to format file system."
        exit 1
    fi
fi
