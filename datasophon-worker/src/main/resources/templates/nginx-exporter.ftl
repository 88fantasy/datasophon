#!/bin/bash

# 定义服务启动命令
BASE_DIR="${nginxBasePath}/nginx-exporter"
LOG_DIR="$BASE_DIR/logs"
LOG_FILE="$LOG_DIR/exporter.log"
PID_FILE="$BASE_DIR/nginx-exporter.pid"

START_CMD="nohup $BASE_DIR/nginx-prometheus-exporter --web.listen-address=:${nginxExporterPort} -nginx.scrape-uri=http://127.0.0.1:${nginxStatusPort}/nginx_status > $LOG_FILE 2>&1 &"

# 检查并创建必要的目录
ensure_dirs_exist() {
    mkdir -p "$LOG_DIR"
}

# 检查进程是否运行
is_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null; then
            return 0
        fi
    fi
    return 1
}

start() {
    ensure_dirs_exist # 确保所有需要的目录都存在

    if is_running; then
        echo "nginx-exporter is already running."
        exit 1
    fi

    # 启动服务
    eval $START_CMD
    echo $! > "$PID_FILE"
    echo "nginx-exporter started, pid: $(cat $PID_FILE)"
    exit 0
}

stop() {
    if ! is_running; then
        echo "nginx-exporter is not running."
        exit 1
    fi

    # 停止服务
    PID=$(cat "$PID_FILE")
    kill $PID
    rm -f "$PID_FILE"
    echo "nginx-exporter stopped."
    exit 0
}

status() {
    if is_running; then
        echo "nginx-exporter is running, pid: $(cat $PID_FILE)"
        exit 0
    else
        echo "nginx-exporter is not running."
        exit 1
    fi
}

restart() {
    stop
    start
}

case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    status)
        status
        ;;
    restart)
        restart
        ;;
    *)
        echo "Usage: $0 {start|stop|status|restart}"
        exit 1
        ;;
esac

exit 0