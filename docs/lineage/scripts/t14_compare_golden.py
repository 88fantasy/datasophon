#!/usr/bin/env python3
"""T14: golden 逐字段比对。

用法：
  1. 导出 golden 期望值（来自 docs/lineage/dwd层表数据示例（...）.xlsx，已转存为 CSV）：
       docs/lineage/data/golden_dwd_expected.csv
  2. 导出 Doris 实际值（在 ddh-01 本机 loopback 免密执行，避免密码进命令行）：
       ssh root@192.168.10.131 "mysql -h127.0.0.1 -P9030 -uroot --batch --raw -e \
         \"SELECT <42列，顺序与golden CSV一致> FROM lineage_flink_verify.dwd_odr_oper_surgery_records_full_hourly \
           WHERE surgery_id IN (<19个golden surgery_id>) ORDER BY surgery_id;\"" \
         > /tmp/doris_actual.tsv
  3. python3 t14_compare_golden.py <golden.csv> <doris_actual.tsv>

比对规则：
  - 按 surgery_id 对齐行，逐列比对
  - etl_time 列非确定性（CAST(NOW() AS STRING)），排除比对
  - golden CSV 里的空字符串 与 Doris TSV 里的字面量 "NULL" 视为等价（均代表 SQL NULL）
  - 退出码 0 = 全部字段精确匹配；非 0 = 存在不一致，详情打印到 stdout
"""
import csv
import sys

EXCLUDED_COLUMNS = {"etl_time"}


def normalize(value):
    if value is None:
        return ""
    if value == "NULL":
        return ""
    return value


def load_csv(path, delimiter=","):
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f, delimiter=delimiter)
        rows = list(reader)
    header = rows[0]
    data = rows[1:]
    return header, data


def main():
    if len(sys.argv) != 3:
        print(f"用法: {sys.argv[0]} <golden.csv> <doris_actual.tsv>", file=sys.stderr)
        return 2

    golden_path, actual_path = sys.argv[1], sys.argv[2]
    golden_header, golden_rows = load_csv(golden_path, delimiter=",")
    actual_header, actual_rows = load_csv(actual_path, delimiter="\t")

    if golden_header != actual_header:
        print("列名或列顺序不一致，无法比对：")
        print("  golden:", golden_header)
        print("  actual:", actual_header)
        return 2

    golden_by_id = {row[0]: dict(zip(golden_header, row)) for row in golden_rows}
    actual_by_id = {row[0]: dict(zip(actual_header, row)) for row in actual_rows}

    golden_ids = set(golden_by_id)
    actual_ids = set(actual_by_id)
    missing_in_actual = golden_ids - actual_ids
    unexpected_in_actual = actual_ids - golden_ids

    # surgery_id 本身也计入比对列（与 T9 验证阶段"42列×19行=798字段"的口径保持一致），
    # 虽然它同时是行对齐键，实际比对时天然精确匹配，但计入总数以保持历史文档口径可比。
    compared_columns = [c for c in golden_header if c not in EXCLUDED_COLUMNS]

    mismatches = []
    field_count = 0
    for sid in sorted(golden_ids & actual_ids):
        g = golden_by_id[sid]
        a = actual_by_id[sid]
        for col in compared_columns:
            field_count += 1
            gv = normalize(g.get(col))
            av = normalize(a.get(col))
            if gv != av:
                mismatches.append((sid, col, gv, av))

    print(f"golden 行数: {len(golden_ids)}")
    print(f"actual 行数: {len(actual_ids)}")
    print(f"比对列数（排除非确定性的 etl_time）: {len(compared_columns)}")
    print(f"比对字段总数: {field_count}")
    print(f"缺失（golden 有、actual 无）: {len(missing_in_actual)} -> {sorted(missing_in_actual)}")
    print(f"多余（actual 有、golden 无，非本次比对范围内的正常现象）: {len(unexpected_in_actual)}")
    print(f"不一致字段数: {len(mismatches)}")

    if mismatches:
        print("\n=== 不一致明细 ===")
        for sid, col, gv, av in mismatches:
            print(f"  surgery_id={sid} column={col} expected={gv!r} actual={av!r}")

    ok = not mismatches and not missing_in_actual
    print(f"\n结论: {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
