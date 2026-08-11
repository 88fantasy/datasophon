#!/usr/bin/env python3
"""持续向 T6 MySQL CDC 源表写入合成数据，维持 Flink 流速指标。

仅操作 lineage_flink_verify.pat_surgery 中 ID 以 CDC_FLOW_SIM_ 开头的记录。
默认每秒随机插入 2--5 行，并每五分钟删除 CREAT_TIME 超过 24 小时的本脚本数据。
依赖目标主机已有 mysql 客户端和受保护的 defaults 文件；密码不会出现在脚本、参数或日志中。

示例：
  python3 t16_mysql_flow_simulator.py --once
  nohup python3 t16_mysql_flow_simulator.py >> simulator.log 2>&1 &
"""

import argparse
import datetime as dt
import os
import random
import re
import signal
import subprocess
import sys
import time
import uuid
from pathlib import Path
DATABASE = "lineage_flink_verify"
TABLE = "pat_surgery"
DEFAULTS_FILE = "/root/.my_gravitino_probe.cnf"
DEFAULT_PREFIX = "CDC_FLOW_SIM_"
CLEANUP_INTERVAL_SECONDS = 300
PREFIX_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]{2,63}$")
RUNNING = True


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--defaults-file",
        default=DEFAULTS_FILE,
        help=f"mysql client defaults 文件（默认：{DEFAULTS_FILE}）",
    )
    parser.add_argument("--min-rows", type=int, default=2, help="每秒最少插入行数（默认：2）")
    parser.add_argument("--max-rows", type=int, default=5, help="每秒最多插入行数（默认：5）")
    parser.add_argument(
        "--retention-hours", type=int, default=24, help="模拟数据保留小时数（默认：24）"
    )
    parser.add_argument(
        "--prefix", default=DEFAULT_PREFIX, help=f"仅写入/清理该 ID 前缀（默认：{DEFAULT_PREFIX}）"
    )
    parser.add_argument("--once", action="store_true", help="只执行一批随机插入后退出")
    parser.add_argument("--cleanup-only", action="store_true", help="只执行一次过期模拟数据清理后退出")
    args = parser.parse_args()
    if args.min_rows < 1 or args.max_rows < args.min_rows:
        parser.error("要求 1 <= --min-rows <= --max-rows")
    if args.retention_hours < 1:
        parser.error("--retention-hours 必须至少为 1")
    if not PREFIX_PATTERN.fullmatch(args.prefix):
        parser.error("--prefix 只能包含大写字母、数字和下划线，且以大写字母开头")
    return args


def mysql(defaults_file: Path, sql: str) -> str:
    completed = subprocess.run(
        [
            "mysql",
            f"--defaults-extra-file={defaults_file}",
            "--batch",
            "--skip-column-names",
            f"--database={DATABASE}",
            f"--execute={sql}",
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    if completed.returncode:
        detail = completed.stderr.strip() or "mysql 命令执行失败"
        raise RuntimeError(detail)
    return completed.stdout.strip()


def sql_string(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"


def record_values(prefix: str, now: dt.datetime) -> str:
    created_at = now.strftime("%Y-%m-%d %H:%M:%S")
    token = uuid.uuid4().hex
    values = {
        "ID": f"{prefix}{now.strftime('%Y%m%d%H%M%S')}_{token}",
        "PATIENT_ID": f"SIM_PATIENT_{token[:16]}",
        "RECORD_ID": f"SIM_RECORD_{token[:16]}",
        "RECORD_TYPE": "SIMULATION",
        "TYPE": "1",
        "GOING_STATUS": "SIMULATED",
        "NAME": "模拟患者",
        "GENDER": "未知",
        "AGE": "0岁",
        "HOSPITAL_ID": "SIM_HOSPITAL",
        "HOSPITAL_NAME": "模拟医院",
        "DEPT_ID": "SIM_DEPT",
        "DEPT_NAME": "模拟科室",
        "SURGERY_DATE": created_at,
        "SURGERY_NAME": "流速模拟手术",
        "IS_TRANSFER_ROOM": "0",
        "STATE": "已安排",
        "CREAT_TIME": created_at,
        "MODIFY_TIME": created_at,
        "VALID": "1",
    }
    return "(" + ", ".join(sql_string(value) for value in values.values()) + ")"


def insert_sql(prefix: str, rows: int, now: dt.datetime) -> str:
    columns = (
        "ID, PATIENT_ID, RECORD_ID, RECORD_TYPE, TYPE, GOING_STATUS, NAME, GENDER, AGE, "
        "HOSPITAL_ID, HOSPITAL_NAME, DEPT_ID, DEPT_NAME, SURGERY_DATE, SURGERY_NAME, "
        "IS_TRANSFER_ROOM, STATE, CREAT_TIME, MODIFY_TIME, VALID"
    )
    return f"INSERT INTO {TABLE} ({columns}) VALUES " + ", ".join(
        record_values(prefix, now) for _ in range(rows)
    )


def cleanup_sql(prefix: str, retention_hours: int, now: dt.datetime) -> str:
    cutoff = (now - dt.timedelta(hours=retention_hours)).strftime("%Y-%m-%d %H:%M:%S")
    pattern = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    return (
        f"DELETE FROM {TABLE} WHERE ID LIKE {sql_string(pattern)} ESCAPE '\\\\' "
        f"AND CREAT_TIME < {sql_string(cutoff)}"
    )


def validate_defaults_file(defaults_file: Path) -> None:
    if not defaults_file.is_file():
        raise RuntimeError("mysql defaults 文件不存在")
    if os.stat(defaults_file).st_mode & 0o077:
        raise RuntimeError("mysql defaults 文件权限过宽；应仅允许所有者读取")


def print_status(message: str) -> None:
    print(f"{dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S')} {message}", flush=True)


def stop_handler(_: int, __: object) -> None:
    global RUNNING
    RUNNING = False


def main() -> int:
    args = parse_args()
    defaults_file = Path(args.defaults_file)
    validate_defaults_file(defaults_file)
    signal.signal(signal.SIGINT, stop_handler)
    signal.signal(signal.SIGTERM, stop_handler)

    now = dt.datetime.now()
    if args.cleanup_only:
        mysql(defaults_file, cleanup_sql(args.prefix, args.retention_hours, now))
        print_status(f"已清理超过 {args.retention_hours} 小时的模拟数据")
        return 0

    inserted_total = 0
    failed_batches = 0
    last_cleanup = 0.0
    last_report = time.monotonic()
    next_tick = time.monotonic()
    print_status(
        f"模拟器启动：表={DATABASE}.{TABLE}，速率={args.min_rows}-{args.max_rows} 行/秒，"
        f"保留={args.retention_hours} 小时"
    )

    while RUNNING:
        now_monotonic = time.monotonic()
        if now_monotonic < next_tick:
            time.sleep(next_tick - now_monotonic)
        now = dt.datetime.now()
        if now_monotonic - last_cleanup >= CLEANUP_INTERVAL_SECONDS or last_cleanup == 0.0:
            try:
                mysql(defaults_file, cleanup_sql(args.prefix, args.retention_hours, now))
                last_cleanup = time.monotonic()
            except RuntimeError as error:
                failed_batches += 1
                print_status(f"清理失败：{error}")

        rows = random.randint(args.min_rows, args.max_rows)
        try:
            mysql(defaults_file, insert_sql(args.prefix, rows, now))
            inserted_total += rows
        except RuntimeError as error:
            failed_batches += 1
            print_status(f"写入失败：{error}")

        if args.once:
            print_status(f"单批写入完成：{rows} 行")
            return 0
        if time.monotonic() - last_report >= 60:
            print_status(f"运行中：本进程已写入 {inserted_total} 行，失败批次 {failed_batches}")
            last_report = time.monotonic()
        next_tick += 1.0
        if next_tick < time.monotonic():
            next_tick = time.monotonic() + 1.0

    print_status(f"模拟器已停止：本进程共写入 {inserted_total} 行，失败批次 {failed_batches}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print_status(f"启动失败：{error}")
        sys.exit(1)
