#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_HOME="$(dirname "$SCRIPT_DIR")"
echo "INSTALL_HOME is : $INSTALL_HOME"

ZOO_CFG="$INSTALL_HOME/conf/zoo.cfg"


get_zookeeper_vote_addr() {
    local config_file="$1"

    if [ ! -f "$config_file" ]; then
        return 1
    fi

    local data_dir=$(grep '^[[:space:]]*dataDir' "$config_file" | head -1 | cut -d'=' -f2 | tr -d ' ')
    if [ -z "$data_dir" ]; then
        return 1
    fi

    local myid_file="$data_dir/myid"
    if [ ! -f "$myid_file" ]; then
        return 1
    fi

    local myid=$(cat "$myid_file" 2>/dev/null | tr -d ' ' | tr -d '\n')
    if [ -z "$myid" ]; then
        return 1
    fi

    local server_config=$(grep "^[[:space:]]*server\.$myid=" "$config_file" | head -1 | cut -d'=' -f2 | tr -d ' ')

    if [ -z "$server_config" ]; then
        return 1
    fi

    port=$(echo "$server_config" | cut -d':' -f3)
    host=$(echo "$server_config" | cut -d':' -f1)

    if [ -z "$port" ]; then
        return 1
    fi
    if [ -z "$host" ]; then
       return 1;
    fi

    if ! [[ "$port" =~ ^[0-9]+$ ]]; then
        return 1
    fi

    # 输出端口号
    echo "$host:$port"
    return 0
}



check_status() {
#    优先使用端口检查
    if command -v netstat >/dev/null 2>&1; then
        addr=$(get_zookeeper_vote_addr "$ZOO_CFG")
        if [ -n "$port" ]; then
            if netstat -ntlp 2>/dev/null | grep -q "$addr "; then
                        return 0
            fi
        fi
    fi
    zk_pid=$(ps -ef | grep -v grep  | grep "org.apache.zookeeper.server.quorum.QuorumPeerMain" | awk '{print $2}' | head -1)
    if [ -n "$zk_pid" ]; then
        return 0
    else
        return 1
    fi
}



check_status
# 根据返回状态设置退出码
if [ $? -eq 0 ]; then
    echo "zookeeper is running"
    exit 0
else
    echo "zookeeper is not running"
    exit 1
fi