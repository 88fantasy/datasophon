# datasophon-cli init mysql_app_db

## 用途

在已安装的 MySQL 上创建应用数据库和对应账号，并授予权限（`bigdata` 账号仅授权目标 DB，其余账号授全库权限）。先用 [`create mysql`](../../create/mysql.md) 完成 MySQL 安装后再执行本命令。

> `create mysql` 在配置文件模式下会自动依次调用本命令，按 `global.mysql.appDbs` 列表创建所有应用账号；通常不需要手动单独执行。

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

被 `create cluster` DAG（步骤 25 `init-mysql-app-db`）或 `create mysql -c <config>` 调用时，对应字段：

| 字段 | 说明 |
|---|---|
| `global.mysql.node` | MySQL 节点 hostname（用于 DAG 节点选择） |
| `global.mysql.password` | 自动传入 `--rootPassword` |
| `global.mysql.port` | 自动传入 `--mysqlPort` |
| `global.mysql.appDbs[*].account` | 自动传入 `-a` |
| `global.mysql.appDbs[*].password` | 自动传入 `-p` |
| `global.mysql.appDbs[*].dbName` | 自动传入 `-d` |

`appDbs` 是数组，DAG 会按列表顺序为每条记录调用本命令一次。

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
| `MySQL 连接失败` | root 密码错误或 MySQL 未启动 | 确认 `create mysql` 已成功；root 密码与 `--rootPassword` 传入值一致 |
| 数据库已存在 | 重复执行 | 幂等操作，不报错 |

## 相关命令

- [`create mysql`](../../create/mysql.md) — 安装 MySQL；配置文件模式下会自动创建 `appDbs`
- [DAG 步骤表](../../../reference/init-all-dag.md)
