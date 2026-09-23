#!/bin/bash
usage="Usage: control.sh (start|stop|restart|status) <master|worker>"

if [[ $# -ne 2 ]]; then
  echo "$usage"
  exit 1
fi

action=$1
role=$2
case "$role" in
  master|worker) ;;
  *) echo "$usage"; exit 1 ;;
esac

current_path=$(cd -- "$(dirname -- "$0")" && pwd -P)
HOME_DIR=$current_path
PID_DIR=$HOME_DIR/pid
LOG_DIR=$HOME_DIR/logs
STOP_TIMEOUT=${STOP_TIMEOUT:-60}
pid_file=$PID_DIR/seatunnel-$role.pid
log_file=$LOG_DIR/control-$role.log
out_file=$LOG_DIR/seatunnel-$role.out

mkdir -p "$PID_DIR" "$LOG_DIR"
# 按角色加锁：同机 master/worker 互不阻塞；status 只读不加锁。
# 等待上限要覆盖一次完整 stop（STOP_TIMEOUT + SIGKILL 收尾），否则并发 restart 会误报失败。
if [[ "$action" != status ]]; then
  exec 200>"$PID_DIR/control-$role.lock"
  flock -w $((STOP_TIMEOUT + 30)) 200 || { echo "another control.sh $role operation holds the lock, giving up"; exit 1; }
fi

log() {
  printf '%s %s\n' "$(date '+%F %T')" "$*" | tee -a "$log_file"
}

read_pid() {
  local pid
  [[ -r "$pid_file" ]] || return 1
  IFS= read -r pid < "$pid_file"
  [[ "$pid" =~ ^0*[1-9][0-9]*$ ]] || return 1
  printf '%s\n' "$pid"
}

is_running() {
  local pid command
  pid=$(read_pid) || return 1
  kill -0 "$pid" >/dev/null 2>&1 || return 1
  command=$(ps -ww -p "$pid" -o command= 2>/dev/null) || return 1
  [[ "$command" == *"-Dseatunnel.home=$HOME_DIR"* &&
    "$command" == *"seatunnel-engine-$role"* &&
    "$command" == *"org.apache.seatunnel.core.starter.seatunnel.SeaTunnelServer"* ]] ||
    [[ "$command" == *"$HOME_DIR/bin/seatunnel-cluster.sh"* &&
      "$command" == *"-r $role"* ]]
}

find_seatunnel_pid() {
  ps -ww -axo pid=,command= | awk -v home="$HOME_DIR" -v role="$role" '
    index($0, "-Dseatunnel.home=" home) &&
    index($0, "seatunnel-engine-" role) &&
    index($0, "org.apache.seatunnel.core.starter.seatunnel.SeaTunnelServer") {
      print $1
      exit
    }'
}

start() {
  local launcher_pid daemon_pid attempt
  if is_running; then
    log "SeaTunnel $role is already running as $(read_pid)"
    return 0
  fi

  [[ -x "$HOME_DIR/bin/seatunnel-cluster.sh" ]] || { log "missing executable bin/seatunnel-cluster.sh"; return 1; }
  [[ -f "$HOME_DIR/config/seatunnel-$role.yaml" ]] || { log "missing config/seatunnel-$role.yaml"; return 1; }
  export SEATUNNEL_HOME=$HOME_DIR
  export SEATUNNEL_CONFIG=$HOME_DIR/config/seatunnel-$role.yaml

  log "starting SeaTunnel $role; output is $out_file"
  nohup "$HOME_DIR/bin/seatunnel-cluster.sh" -d -r "$role" > "$out_file" 2>&1 </dev/null &
  launcher_pid=$!
  printf '%s\n' "$launcher_pid" > "$pid_file"

  # seatunnel-cluster.sh -d forks the JVM and exits within seconds, so only the JVM pid
  # is worth recording: a pid file left pointing at the launcher makes every later
  # `status` report "not running" even though the server is up.
  for ((attempt = 0; attempt < 30; attempt++)); do
    sleep 1
    daemon_pid=$(find_seatunnel_pid)
    if [[ -n "$daemon_pid" ]]; then
      printf '%s\n' "$daemon_pid" > "$pid_file"
      log "SeaTunnel $role started as $daemon_pid"
      return 0
    fi
  done
  rm -f "$pid_file"
  log "SeaTunnel $role did not start; see $out_file"
  return 1
}

stop() {
  local pid waited
  pid=$(read_pid) || { log "SeaTunnel $role is not running"; rm -f "$pid_file"; return 0; }
  if ! is_running; then
    log "SeaTunnel $role is not running"
    rm -f "$pid_file"
    return 0
  fi

  log "sending SIGTERM to SeaTunnel $role pid $pid"
  kill -TERM "$pid" || { log "failed to send SIGTERM to $pid"; return 1; }
  for ((waited = 0; waited < STOP_TIMEOUT; waited++)); do
    if ! is_running; then
      rm -f "$pid_file"
      log "SeaTunnel $role stopped"
      return 0
    fi
    sleep 1
  done

  log "SeaTunnel $role did not stop after $STOP_TIMEOUT seconds; sending SIGKILL to $pid"
  kill -KILL "$pid" || { log "failed to send SIGKILL to $pid"; return 1; }
  sleep 1
  if is_running; then
    log "SeaTunnel $role pid $pid is still alive after SIGKILL"
    return 1
  fi
  rm -f "$pid_file"
  log "SeaTunnel $role stopped after SIGKILL"
}

status() {
  if is_running; then
    log "SeaTunnel $role is running as $(read_pid)"
    return 0
  fi
  log "SeaTunnel $role is not running"
  return 1
}

case "$action" in
  start) start ;;
  stop) stop ;;
  restart) stop && sleep 2 && start ;;
  status) status ;;
  *) echo "$usage"; exit 1 ;;
esac
