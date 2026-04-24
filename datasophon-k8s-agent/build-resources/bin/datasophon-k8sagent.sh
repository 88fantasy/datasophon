#!/bin/bash

JAVA_CMD="java"
#if [ -n "$JAVA8_HOME" ] && [ -x "$JAVA8_HOME/bin/java" ]; then
#    JAVA_CMD="$JAVA8_HOME/bin/java"
#elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
#    JAVA_CMD="$JAVA_HOME/bin/java"
#else
#    JAVA_CMD=$(command -v java 2>/dev/null)
#fi
if [ -z "$JAVA_CMD" ]; then
    echo "can not find java command...."
    exit 1
fi

echo "use java: $JAVA_CMD"

MAIN_CLASS="com.datasophon.k8sagent.DatasophonK8sAgentApplication"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"
echo "working dir ${BASE_DIR}"

cd "${BASE_DIR}"

get_pid() {
     pid=`ps -ef | grep -v grep | grep "${BASE_DIR}/lib" | grep "${MAIN_CLASS}" | awk '{print $2}'`
     echo $pid
}

status(){
    pid="$(get_pid)"
    if [ -z "$pid" ]; then
        return 0
    else
        return 1
    fi
}

start(){
    status >/dev/null 2>&1
    if [ $? -eq 1 ]; then
        echo "k8s-agent is running, do not need to start"
        print_running
        return 0
    fi
    mkdir -p "$BASE_DIR/logs"

#   见: PropertyUtils的坑爹规则
    export DDH_HOME="$BASE_DIR"

    HEAP_OPTS="-Xms512m -Xmx512m -XX:MetaspaceSize=256M -XX:MaxMetaspaceSize=512M"
    CONF_PATH="-Dspring.config.location=file:$BASE_DIR/conf/application.yaml"
    CLASS_PATH="-classpath $BASE_DIR/conf:$BASE_DIR/lib/*"
    DEBUG_OPT=""
    if [ -n "$DEBUG" ]; then
          DEBUG_OPT="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=30105"
    fi
    EXEC_CMD="$JAVA_CMD $DEBUG_OPT $HEAP_OPTS $JAVA_OPT $CONF_PATH $CLASS_PATH  $MAIN_CLASS"
    if [ -n "$IS_DOCKER" ]; then
       $EXEC_CMD
    else
        log_path="$BASE_DIR/logs/datasophon-k8sagent.out"
        echo "Starting datasophon-k8s-agent, logging to $log_path"
        nohup $EXEC_CMD > $log_path 2>&1 &
        print_running
    fi
}

stop(){
    status >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "k8s-agent is not running"
    else
        pid="$(get_pid)"
        if [ -n "$pid" ]; then
            echo "Stopping datasophon-k8s-agent (PID: $pid)..."
            kill $pid
            sleep 5
            if kill -0 $pid > /dev/null 2>&1; then
                echo "datasophon-k8s-agent did not stop gracefully, forcing kill"
                kill -9 $pid
            fi
            echo "datasophon-k8s-agent stopped"
        fi
    fi
}

print_running() {
    echo ""
    pid="$(get_pid)"
    if [ -z "$pid" ]; then
        echo "datasophon-k8s-agent is not Running....."
    else
        echo "datasophon-k8s-agent is Running Now..... pid is ${pid} "
    fi
}

case $1 in
    start )
        echo ""
        echo "datasophon-k8s-agent Starting........... "
        echo ""
        start
    ;;

    stop )
        echo ""
        echo "datasophon-k8s-agent Stopping.......... "
        echo ""
        stop
    ;;

    restart )
        echo "datasophon-k8s-agent is Restarting.......... "
        echo "datasophon-k8s-agent Stopping.......... "
        stop
        echo ""
        echo "datasophon-k8s-agent is Starting.......... "
        start
    ;;

    status )
        status >/dev/null 2>&1
        if [ $? -eq 0 ]; then
            echo ""
            echo "datasophon-k8s-agent is not Running......"
            echo ""
        else
            echo ""
            pid="$(get_pid)"
            echo "datasophon-k8s-agent is Running..... pid is ${pid}"
            echo ""
        fi
    ;;

    * )
        echo "Usage: datasophon-k8sagent.sh (start|stop|status|restart)"
        exit 1
esac
