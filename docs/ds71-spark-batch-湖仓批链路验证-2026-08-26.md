# ds71 Spark 批实现：SQL 全文、真值表与语义替换对照

> 本文档是 `docs/ds71-lakehouse-Spark批实现与批链路验证-实施方案-2026-08-26.md`（下称"方案文档"）Phase 5 要求的产出物，记录实际落地的 7 段 SQL 全文、样例数据、真值推导与语义替换对照。执行过程、探针结论、踩坑记录见方案文档本身，本文档只留最终形态。
>
> **架构说明**：方案文档最初计划用 Paimon + S3 复现"湖仓"形态，但 Phase 0 探针证实 openlineage-spark（含最新 1.52.0）从未支持 Paimon 的输出数据集识别（官方 issue #3870 至今 open）。经用户决策，本轮改为 **Spark JDBC 直写 Doris**，标题沿用方案文档原名，实际不再涉及 Paimon/S3。
>
> **样例数据说明**：《实时湖仓技术方案》原文档不在本仓库内（已确认搜索不到），下方 orders/orders_pay/product_catalog 三表数据为反推构造——满足方案文档 Phase 4① 预先声明的真值表约束（7/7/5/7/6/3/4 的行数、dwm 去重后的 6 个 (user,shop) 组合、shop 12347 的 uv=2/pv=3/7000 交叉校验），不是对原文档样例数据的逐字复刻。

---

## 1. 样例数据

### 1.1 MySQL `order_dw` 源库

```sql
CREATE DATABASE IF NOT EXISTS order_dw;
USE order_dw;

CREATE TABLE orders (
  order_id VARCHAR(32) PRIMARY KEY,
  user_id VARCHAR(32) NOT NULL,
  shop_id VARCHAR(32) NOT NULL,
  product_id VARCHAR(32) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  order_time DATETIME NOT NULL
);

CREATE TABLE orders_pay (
  pay_id VARCHAR(32) PRIMARY KEY,
  order_id VARCHAR(32) NOT NULL,
  pay_platform VARCHAR(16) NOT NULL,
  pay_amount DECIMAL(10,2) NOT NULL,
  pay_time DATETIME NOT NULL
);

CREATE TABLE product_catalog (
  product_id VARCHAR(32) PRIMARY KEY,
  product_name VARCHAR(64) NOT NULL,
  category VARCHAR(32) NOT NULL
);

INSERT INTO orders VALUES
('O001','user_001','12345','P001',1500.00,'2026-08-01 10:00:00'),
('O002','user_002','12346','P002',800.00,'2026-08-01 11:00:00'),
('O003','user_003','12347','P003',3000.00,'2026-08-02 09:00:00'),
('O004','user_003','12347','P004',2000.00,'2026-08-02 10:30:00'),
('O005','user_001','12347','P005',2000.00,'2026-08-02 14:00:00'),
('O006','user_002','12348','P001',1200.00,'2026-08-03 09:00:00'),
('O007','user_001','12348','P002',900.00,'2026-08-03 15:00:00');

INSERT INTO orders_pay VALUES
('PAY001','O001','alipay',1500.00,'2026-08-01 10:01:00'),
('PAY002','O002','wechat',800.00,'2026-08-01 11:01:00'),
('PAY003','O003','wechat',3000.00,'2026-08-02 09:01:00'),
('PAY004','O004','alipay',2000.00,'2026-08-02 10:31:00'),
('PAY005','O005','unionpay',2000.00,'2026-08-02 14:01:00'),
('PAY006','O006','alipay',1200.00,'2026-08-03 09:01:00'),
('PAY007','O007','wechat',900.00,'2026-08-03 15:01:00');

INSERT INTO product_catalog VALUES
('P001','手机','电子产品'),
('P002','耳机','电子产品'),
('P003','T恤','服装'),
('P004','牛仔裤','服装'),
('P005','咖啡杯','家居');
```

每笔订单恰有 1 笔支付（7:7 一一对应）；3 个用户（user_001/002/003）× 4 个商户（12345/12346/12347/12348），覆盖 6 个不同的 (user, shop) 组合；5 个商品分属 3 个品类（电子产品/服装/家居）。

### 1.2 Phase 4④ 追加的新数据（历史隔离验证用）

```sql
INSERT INTO orders VALUES
('O008','user_002','12345','P002',500.00,'2026-08-04 10:00:00'),
('O009','user_001','12346','P003',700.00,'2026-08-04 11:00:00'),
('O010','user_003','12348','P004',1000.00,'2026-08-04 12:00:00');

INSERT INTO orders_pay VALUES
('PAY008','O008','wechat',500.00,'2026-08-04 10:01:00'),
('PAY009','O009','alipay',700.00,'2026-08-04 11:01:00'),
('PAY010','O010','unionpay',1000.00,'2026-08-04 12:01:00');
```

3 笔新订单复用已有的 3 个用户与 4 个商户（不引入新用户/新商户），但落在此前未出现过的 (user, shop) 组合上，用于验证"重跑后 dwm 层去重集合扩大、但 dws 层用户数/商户数不变"这条推导链路。

---

## 2. Doris `ds71` 库 7 张目标表 DDL

```sql
CREATE DATABASE IF NOT EXISTS ds71;

CREATE TABLE ods_orders (
  order_id VARCHAR(32), user_id VARCHAR(32), shop_id VARCHAR(32),
  product_id VARCHAR(32), amount DECIMAL(10,2), order_time DATETIME
) DISTRIBUTED BY HASH(order_id) BUCKETS 1 PROPERTIES('replication_num'='1');

CREATE TABLE ods_orders_pay (
  pay_id VARCHAR(32), order_id VARCHAR(32), pay_platform VARCHAR(16),
  pay_amount DECIMAL(10,2), pay_time DATETIME
) DISTRIBUTED BY HASH(pay_id) BUCKETS 1 PROPERTIES('replication_num'='1');

CREATE TABLE ods_product_catalog (
  product_id VARCHAR(32), product_name VARCHAR(64), category VARCHAR(32)
) DISTRIBUTED BY HASH(product_id) BUCKETS 1 PROPERTIES('replication_num'='1');

CREATE TABLE dwd_orders (
  order_id VARCHAR(32), user_id VARCHAR(32), shop_id VARCHAR(32),
  product_id VARCHAR(32), product_name VARCHAR(64), category VARCHAR(32),
  amount DECIMAL(10,2), pay_platform VARCHAR(16), pay_amount DECIMAL(10,2),
  order_time DATETIME
) DISTRIBUTED BY HASH(order_id) BUCKETS 1 PROPERTIES('replication_num'='1');

CREATE TABLE dwm_users_shops (
  user_id VARCHAR(32), shop_id VARCHAR(32),
  buy_fee_sum DECIMAL(12,2), buy_cnt BIGINT
) DISTRIBUTED BY HASH(user_id) BUCKETS 1 PROPERTIES('replication_num'='1');

CREATE TABLE dws_users (
  user_id VARCHAR(32), buy_shop_cnt BIGINT, total_buy_fee_sum DECIMAL(12,2)
) DISTRIBUTED BY HASH(user_id) BUCKETS 1 PROPERTIES('replication_num'='1');

CREATE TABLE dws_shops (
  shop_id VARCHAR(32), uv BIGINT, pv BIGINT, payed_buy_fee_sum DECIMAL(12,2)
) DISTRIBUTED BY HASH(shop_id) BUCKETS 1 PROPERTIES('replication_num'='1');
```

**必须先建好这 7 张表再让 DS 任务跑**——Spark 的 `CREATE TABLE ... USING jdbc` 只能注册已存在的远端表，不会替你建表（见 §4 踩坑记录）。

---

## 3. 7 段 Spark SQL 全文

每段是一个独立的 DS SPARK 任务（`taskType=SPARK`、`programType=SQL`、`deployMode=local`、`sparkVersion=SPARK2`、`workerGroup=default`）。`others` 字段统一为：

```
--conf spark.datasophon.dsTaskInstanceId=ds-1-${system.task.instance.id}
--conf spark.sql.legacy.charVarcharAsString=true
--name ds71-<层名>-${system.task.instance.id}
```

`spark.sql.legacy.charVarcharAsString=true` **每段必须带**，否则字符串列会被右侧空格填充到声明宽度（见 §4）。

以下 SQL 里的 `<DORIS_USER>`/`<DORIS_PW>` 为 Doris 侧 `ds71_batch` 账号密码，`<MYSQL_USER>`/`<MYSQL_PW>` 为 `order_dw` 库的 MySQL 账号密码；实际部署时通过远端文件/环境变量注入，不进代码库明文。

### 3.1 ods_orders

```sql
CREATE TABLE src_orders USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:3306/order_dw", dbtable "orders",
  user "<MYSQL_USER>", password "<MYSQL_PW>", driver "com.mysql.cj.jdbc.Driver");

CREATE TABLE ods_orders USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.ods_orders",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver", truncate "true");

INSERT OVERWRITE TABLE ods_orders
SELECT order_id, user_id, shop_id, product_id, amount, order_time FROM src_orders;
```

### 3.2 ods_orders_pay

```sql
CREATE TABLE src_pay USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:3306/order_dw", dbtable "orders_pay",
  user "<MYSQL_USER>", password "<MYSQL_PW>", driver "com.mysql.cj.jdbc.Driver");

CREATE TABLE ods_orders_pay USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.ods_orders_pay",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver", truncate "true");

INSERT OVERWRITE TABLE ods_orders_pay
SELECT pay_id, order_id, pay_platform, pay_amount, pay_time FROM src_pay;
```

### 3.3 ods_product_catalog

```sql
CREATE TABLE src_prod USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:3306/order_dw", dbtable "product_catalog",
  user "<MYSQL_USER>", password "<MYSQL_PW>", driver "com.mysql.cj.jdbc.Driver");

CREATE TABLE ods_product_catalog USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.ods_product_catalog",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver", truncate "true");

INSERT OVERWRITE TABLE ods_product_catalog
SELECT product_id, product_name, category FROM src_prod;
```

### 3.4 dwd_orders（3→1 扇入：三张 ODS JOIN）

```sql
CREATE TABLE src_ods_orders USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.ods_orders",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver");
CREATE TABLE src_ods_pay USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.ods_orders_pay",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver");
CREATE TABLE src_ods_prod USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.ods_product_catalog",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver");
CREATE TABLE dwd_orders USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.dwd_orders",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver", truncate "true");

INSERT OVERWRITE TABLE dwd_orders
SELECT o.order_id, o.user_id, o.shop_id, o.product_id, p.product_name, p.category,
       o.amount, pay.pay_platform, pay.pay_amount, o.order_time
FROM src_ods_orders o
JOIN src_ods_pay pay ON o.order_id = pay.order_id
JOIN src_ods_prod p ON o.product_id = p.product_id;
```

### 3.5 dwm_users_shops（按 user+shop 聚合）

```sql
CREATE TABLE src_dwd USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.dwd_orders",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver");
CREATE TABLE dwm_users_shops USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.dwm_users_shops",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver", truncate "true");

INSERT OVERWRITE TABLE dwm_users_shops
SELECT user_id, shop_id, SUM(amount) AS buy_fee_sum, COUNT(*) AS buy_cnt
FROM src_dwd GROUP BY user_id, shop_id;
```

### 3.6 dws_users（1→2 扇出之一：按 user 聚合）

```sql
CREATE TABLE src_dwm_u USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.dwm_users_shops",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver");
CREATE TABLE dws_users USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.dws_users",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver", truncate "true");

INSERT OVERWRITE TABLE dws_users
SELECT user_id, COUNT(DISTINCT shop_id) AS buy_shop_cnt, SUM(buy_fee_sum) AS total_buy_fee_sum
FROM src_dwm_u GROUP BY user_id;
```

### 3.7 dws_shops（1→2 扇出之二：按 shop 聚合）

```sql
CREATE TABLE src_dwm_s USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.dwm_users_shops",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver");
CREATE TABLE dws_shops USING jdbc OPTIONS (
  url "jdbc:mysql://192.168.10.131:9030/ds71", dbtable "ds71.dws_shops",
  user "<DORIS_USER>", password "<DORIS_PW>", driver "com.mysql.cj.jdbc.Driver", truncate "true");

INSERT OVERWRITE TABLE dws_shops
SELECT shop_id, COUNT(DISTINCT user_id) AS uv, SUM(buy_cnt) AS pv, SUM(buy_fee_sum) AS payed_buy_fee_sum
FROM src_dwm_s GROUP BY shop_id;
```

---

## 4. 真值表与实测比对

### 4.1 首次运行（7 笔订单）

| 节点 | 期望行数 | 实测行数 | 推导 |
|---|---|---|---|
| ods_orders | 7 | **7** | 源表 7 行 |
| ods_orders_pay | 7 | **7** | 源表 7 行 |
| ods_product_catalog | 5 | **5** | 源表 5 行 |
| dwd_orders | 7 | **7** | 每笔订单恰有 1 笔支付，join 后仍 7 行 |
| dwm_users_shops | 6 | **6** | (user,shop) 去重：(001,12345)(002,12346)(003,12347)(001,12347)(002,12348)(001,12348) |
| dws_users | 3 | **3** | user_001 / 002 / 003 |
| dws_shops | 4 | **4** | 12345 / 12346 / 12347 / 12348 |

交叉校验：shop `12347` 期望 `uv=2, pv=3, payed_buy_fee_sum=7000`，实测 `uv=2,pv=3,payed_buy_fee_sum=7000.00`，**精确匹配**。

### 4.2 追加 3 笔订单后重跑（历史隔离验证）

| 节点 | 新真值 | 新实例（13）实测 | 旧实例（12）复核 |
|---|---|---|---|
| ods_orders | 10 | **10** | 仍为 7（未被覆盖显示） |
| ods_orders_pay | 10 | **10** | 仍为 7 |
| ods_product_catalog | 5（未改商品表） | **5** | 仍为 5 |
| dwd_orders | 10 | **10** | 仍为 7 |
| dwm_users_shops | 9（6 旧 + 3 新组合） | **9** | 仍为 6 |
| dws_users | 3（未引入新用户） | **3** | 仍为 3 |
| dws_shops | 4（未引入新商户） | **4** | 仍为 4 |

旧实例（`wf_ds71_batch_spark-20260826105730704`）在 Doris 底层表已被新一次运行整体覆盖写入之后，页面上仍精确显示它自己那次运行的行数——证明血缘绑定按每次运行（run）独立快照，不是查询目标表的实时行数。截图见 `.scratch/ds-workflow-tab/shots/ds71-instance{12-frozen-oldvalues,13-rerun-newvalues}-2026-08-26.png`。

---

## 5. 语义替换对照（原 Flink 流方案 → 本次 Spark 批实现）

| 原方案（Flink 流） | 批实现 | 理由 |
|---|---|---|
| `mysql-cdc` connector | Spark JDBC 批读 | 批语义下没有 CDC，一次性快照即 ODS |
| `merge-engine=partial-update` + `UNION ALL` | 直接 `LEFT JOIN`/`JOIN` orders ⋈ product_catalog ⋈ orders_pay | partial-update 是为流式乱序到达设计的；批场景一次性就能 join 齐 |
| `merge-engine=aggregation` | `GROUP BY` + `SUM`/`COUNT` | 同上，预聚合合并机制在批下退化为普通聚合 |
| `changelog-producer=lookup` | 不需要 | 无下游流式消费 |
| Paimon + S3 warehouse（原计划） | Doris 原生表 + JDBC | openlineage-spark 无 Paimon 输出数据集识别能力，见方案文档 Phase 0-b |

---

## 6. 关键实施坑（简要索引，详细上下文见方案文档偏差记录）

1. Paimon catalog 配置缺 `spark.sql.extensions` 会直接报错。
2. `spark-submit`/`spark-sql` 的 `--properties-file` 是**替换**而非叠加 `spark-defaults.conf`。
3. Paimon 1.4.1 + openlineage-spark 1.29.0（含最新 1.52.0）无输出数据集识别能力，结构性缺口非配置问题。
4. Spark `INSERT OVERWRITE` 对 JDBC 注册表默认 **DROP+CREATE**，必须加 `truncate="true"` 才会做 `TRUNCATE TABLE`。
5. Spark 3.x 对 VARCHAR 走 CHAR 写入侧填充语义，必须加 `spark.sql.legacy.charVarcharAsString=true`，否则字符串被空格填充到声明宽度，静默破坏 JOIN/GROUP BY 正确性。
6. Doris 频繁 DROP+CREATE 同名表会触发短暂的 FE/BE 元数据不一致（"表不存在"），充分等待后自愈。
7. Doris 4.1.3 没有 `INSERT_PRIV` 权限类型；`root` 通过局域网 IP 连接会被拒绝，只有 `127.0.0.1`/`localhost` 特例放行。
8. DS OpenAPI 正确路径需带 `/dolphinscheduler` 前缀；创建任务前需先 `gen-task-codes`；触发执行是 `/executors/start-workflow-instance`（新版本已从 `start-process-instance` 更名）。
9. DS 工作流实例列表的"开始时间"因 `SPRING_JACKSON_TIME_ZONE` 环境变量未赋值而超前约 8 小时（平台既有缺陷，不影响行数判据）。
