#!/bin/bash

usage="Usage: $0 (start|stop|restart|status) [options]"

# if no args specified, show usage
if [ $# -le 0 ]; then
  echo "$usage"
  exit 1
fi

startStop=$1
shift  # Remove the first argument so that any additional options can be passed to Nginx

# Nginx 配置路径
nginx_path="/data/install_datasophon/nginx/nginx"
nginx_bin="${nginx_path}/sbin/nginx"
pid_file="${nginx_path}/sbin/nginx.pid"
conf_file="${nginx_path}/conf/nginx.conf"

# 日志路径
access_log="${nginx_path}/logs/access/access.log"
error_log="${nginx_path}/logs/error/error.log"

# 临时文件路径
client_body_temp_path="${nginx_path}/temp/body"
fastcgi_temp_path="${nginx_path}/temp/fastcgi"
proxy_temp_path="${nginx_path}/temp/proxy"
scgi_temp_path="${nginx_path}/temp/scgi"
uwsgi_temp_path="${nginx_path}/temp/uwsgi"

# 检查并创建必要的目录
create_directories() {
    mkdir -p "${nginx_path}/logs/access"
    mkdir -p "${nginx_path}/logs/error"
    mkdir -p "${nginx_path}/temp/body"
    mkdir -p "${nginx_path}/temp/fastcgi"
    mkdir -p "${nginx_path}/temp/proxy"
    mkdir -p "${nginx_path}/temp/scgi"
    mkdir -p "${nginx_path}/temp/uwsgi"
}

# 检查 Nginx 是否正在运行
is_running() {
    if [ ! -f "$pid_file" ]; then
        return 1
    fi

    pid=$(cat "$pid_file")
    if [ -z "$pid" ] || ! [[ "$pid" =~ ^[0-9]+$ ]]; then
        return 1
    fi

    if ps -p "$pid" > /dev/null; then
        return 0
    else
        return 1
    fi
}

# 打印 Nginx 状态
status() {
    if is_running; then
        echo "Nginx is running (PID: $(cat "$pid_file"))"
        exit 0
    else
        echo "Nginx is not running"
        exit 1
    fi
}

# 启动 Nginx
start() {
    if is_running; then
        echo "Nginx is already running (PID: $(cat "$pid_file"))"
        exit 0
    fi

    # 检查并创建必要的目录
    create_directories

    echo "Starting Nginx..."
    if $nginx_bin -c $conf_file $@; then
        echo "Nginx started successfully (PID: $(cat "$pid_file"))"
        exit 0
    else
        echo "Failed to start Nginx"
        exit 1
    fi
}

# 停止 Nginx
stop() {
    if ! is_running; then
        echo "Nginx is not running"
        exit 0
    fi

    echo "Stopping Nginx..."
    if $nginx_bin -s stop; then
        rm -f "$pid_file"
        echo "Nginx stopped successfully"
        exit 0
    else
        echo "Failed to stop Nginx"
        exit 1
    fi
}

# 重启 Nginx
restart() {
    echo "Restarting Nginx..."

    if is_running; then
        # 如果 Nginx 正在运行，先停止它
        stop
    fi

    # 无论 Nginx 是否在运行，都尝试启动它
    start "$@"
}

# 根据传入的参数执行相应的操作
case $startStop in
  (status)
    status
    ;;
  (start)
    start "$@"
    ;;
  (stop)
    stop
    ;;
  (restart)
    restart
    ;;
  (*)
    echo "$usage"
    exit 1
    ;;
esac

echo "End $startStop."