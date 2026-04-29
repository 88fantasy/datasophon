#!/bin/bash

# 需要设置时间的主机列表（空格分隔）
HOSTS=("192.168.2.213" "192.168.2.173" "192.168.2.200" "192.168.2.132")

# SSH 用户名（需要有 sudo 权限或直接 root）
SSH_USER="root"

# 可选：是否使用 sudo（如果普通用户且配置了 sudo 免密）
USE_SUDO="yes"   # yes / no

# 获取当前本机时间，格式为 "YYYY-MM-DD HH:MM:SS"
CURRENT_TIME=$(date "+%Y-%m-%d %H:%M:%S")

# 循环所有主机
for HOST in "${HOSTS[@]}"; do
    echo "正在设置主机 $HOST 时间为 $CURRENT_TIME ..."

    # 构建远程命令
    if [ "$USE_SUDO" = "yes" ]; then
        REMOTE_CMD="sudo date -s '$CURRENT_TIME'"
    else
        REMOTE_CMD="date -s '$CURRENT_TIME'"
    fi

    # 通过 SSH 执行命令
    ssh ${SSH_USER}@${HOST} "$REMOTE_CMD"

    if [ $? -eq 0 ]; then
        echo "$HOST 时间设置成功"
    else
        echo "$HOST 时间设置失败（请检查 SSH 连接和权限）"
    fi
done

echo "所有主机时间设置完成。"