# datasophon-cli init mysql_app_db

## 用途

在已安装的 MySQL 上创建应用数据库（默认 `datasophon`）和对应账号，并授予全库权限。在 `init mysql` 完成后执行。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init mysql_app_db \
  --rootPassword <password> \
  -a <account> \
  -p <password> \
  -d <dbName> \
  [--mysqlPort <port>] \
  [公共 flag]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--rootPassword` | 无 | string | — | 是 | MySQL root 密码（用于执行建库/建账号操作） |
| `--account` | `-a` | string | — | 是 | 应用账号名 |
| `-p` | `-p` | string | — | 是 | 应用账号密码（注意：flag 名为 `-p`，与 Go flag 约定一致） |
| `--dbName` | `-d` | string | — | 是 | 数据库名 |
| `--mysqlPort` | 无 | int | `3306` | 否 | MySQL 端口 |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

> **注意**：应用账号密码的长 flag 名即为 `-p`（单字符），这是代码层面的历史遗留，不同于 root 密码的 `--rootPassword`。在命令行中通过 `-p <密码>` 传入。

## 配置文件依赖

| 字段 | 说明 |
|---|---|
| `global.mysql.mysqlNode` | MySQL 节点（DAG 节点选择器） |
| `global.mysql.mysqlRootPassword` | 在 DAG 中自动传入 `--rootPassword` |
| `global.mysql.mysqlAccount` / `mysqlPassword` | 在 DAG 中自动传入 `-a` / `-p` |
| `global.mysql.mysqlDbName` | 数据库名，默认 `datasophon` |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init mysql_app_db \
  --rootPassword 'Mysql@123' \
  -a datasophon \
  -p 'App@123' \
  -d datasophon \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init mysql_app_db \
  --rootPassword 'Mysql@123' \
  -a datasophon \
  -p 'App@123' \
  -d datasophon \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `MySQL 连接失败` | root 密码错误或 MySQL 未启动 | 确认 `init mysql` 已成功；密码与 `-p` 传入值一致 |
| 数据库已存在 | 重复执行 | 幂等操作，不报错 |

## 相关命令

- [`init mysql`](./mysql.md) — 先安装 MySQL
- [DAG 步骤表](../../../reference/init-all-dag.md)
