# datasophon-cli init worker_local_properties

## 用途

向节点写入 `datasophon-worker/conf/worker.local.properties`，覆盖 `conf/worker.properties` 里写死的 `mysql.ip=127.0.0.1`、`mysql.password=`（空）默认值。

这两个默认值假设 MySQL 与 Worker 同机部署；多节点集群里除 MySQL 所在节点外，服务安装期的 `InitDbHookAction`（初始化服务数据库表）会用错误的 IP/空密码连接 MySQL，全部失败。`worker.local.properties` 不随 `datasophon-worker.tar.gz` 打包，提前写好后 Worker 解压不会覆盖它，能存活到 Worker 真正启动那一刻被 `PropertyUtils` 加载。

> `create cluster` 在配置文件模式下会自动对全部节点调用本命令；通常不需要手动单独执行。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init worker_local_properties \
  --installPath <path> \
  --mysqlIp <ip> \
  --mysqlPassword <password> \
  [公共 flag]
```

## 参数 / Flags

|       flag         |   类型   | 默认 | 必填 |             说明             |
|--------------------|--------|----|----|------------------------------|
| `--installPath`    | string | —  | 是  | 节点安装根目录（`${InstallPath}`）    |
| `--mysqlIp`         | string | —  | 是  | 真实 MySQL 所在节点 IP            |
| `--mysqlPassword`   | string | —  | 是  | MySQL 密码                    |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

被 `create cluster` DAG（步骤 27 `init-worker-mysql-conf`）调用时，对应字段：

|            字段            |              说明               |
|---------------------------|--------------------------------|
| `global.mysql.node`       | MySQL 节点 hostname，解析为真实 IP 后传入 `--mysqlIp` |
| `global.mysql.password`   | 自动传入 `--mysqlPassword`         |

面向全部节点执行（每个节点写入内容相同），条件同 `mysql.enable`。

## 示例

```bash
datasophon-cli init worker_local_properties \
  --installPath /data/install_datasophon \
  --mysqlIp 192.168.10.131 \
  --mysqlPassword 'Mysql@123' \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 相关命令

- [`init mysql_app_db`](mysql_app_db.md) — 建应用数据库账号
- [DAG 步骤表](../../../reference/init-all-dag.md)
