# 血缘验证样例数据（不入库）

本目录下的数据文件**不随仓库分发**，`.gitignore` 已屏蔽整个目录（本 README 除外）。

## 原因

T5/T7/T14 使用的 golden 与 raw 样例数据直接来自真实医疗生产库，包含患者姓名、住院号、
诊断与手术记录，以及医护人员真实姓名与工号。这类数据属于受保护的个人健康信息，
不得进入公开仓库。同样被移除的还有业务方提供的 `.xlsx`/`.docx` 原始样例，
以及一张暴露了客户方空间名与创建人姓名的血缘页面截图。

## 受影响的文档引用

下列文档记录了验证过程，其中提到的数据文件路径现在只在内网环境有效：

- `docs/lineage/t14_golden_comparison_report_2026-08-07.txt`
- `docs/lineage/scripts/t14_compare_golden.py`（`--golden` / `--actual` 两个入参）
- `docs/data-lineage-Flink实时链路验证-实施方案-2026-08-05.md` 的 T5/T7/T14 行
- `docs/session-handoff-T16-flink-flow-rate-2026-08-07.md`

这些记录本身是历史事实，未做改写；复现验证时需要自行提供数据文件。

## 如何复现验证

仓库内保留了复现所需的全部**结构与逻辑**：

| 保留内容 | 路径 |
|---|---|
| MySQL 源表 DDL | `docs/lineage/ddl/mysql/` |
| Paimon ODS 建表 | `docs/lineage/ddl/paimon/` |
| DWD 清洗 SQL | `docs/lineage/sql/`、`docs/lineage/dwd层建表语句清洗语句.md` |
| CDC 链路 SQL | `docs/lineage/sql/t6_mysql_cdc_to_paimon.sql` |
| golden 比对脚本 | `docs/lineage/scripts/t14_compare_golden.py` |
| 持续流量模拟器 | `docs/lineage/scripts/t16_mysql_flow_simulator.py` |

按上述 DDL 建表后，用符合表结构的**合成数据**灌注即可复跑整条链路。比对脚本按列名
逐字段比对，不依赖具体数值，因此合成数据同样能验证清洗逻辑的正确性。
