# datasophon-cli init mysql

## 用途

在目标节点上安装并初始化 MySQL（解压 tar 包、初始化数据目录、设置 root 密码、配置 `/etc/my.cnf`、启动 mysqld 服务）。Datasophon Manager 依赖 MySQL 存储集群元数据。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init mysql \
  -p <password> \
  --packagePath <dir> \
  --installPath <dir> \
  -x <x86_64-tar> \
  -a <aarch64-tar> \
  [-f] [--mysqlPort <port>] \
  [公共 flag]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--password` | `-p` | string | — | 是 | MySQL root 密码 |
| `--packagePath` | 无 | string | — | 是 | 安装包目录 |
| `--installPath` | 无 | string | — | 是 | MySQL 安装根目录 |
| `--x86Tar` | `-x` | string | — | 是 | x86_64 MySQL tar 包文件名 |
| `--aarch64Tar` | `-a` | string | — | 是 | aarch64 MySQL tar 包文件名 |
| `--force` | `-f` | bool | `false` | 否 | MySQL 已存在时是否强制重装 |
| `--mysqlPort` | 无 | int | `3306` | 否 | MySQL 监听端口 |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

| 字段 | 说明 |
|---|---|
| `global.mysql.mysqlNode` | MySQL 安装节点（DAG 节点选择器） |
| `global.packages.mysql.x86_64` | x86_64 MySQL tar 包文件名（DAG 自动传入 `-x`） |
| `global.packages.mysql.aarch64` | aarch64 MySQL tar 包文件名（DAG 自动传入 `-a`） |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init mysql \
  -p 'Mysql@123' \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/mysql \
  -x mysql-8.0.33-linux-glibc2.12-x86_64.tar.xz \
  -a mysql-8.0.33-linux-glibc2.12-aarch64.tar.xz \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init mysql \
  -p 'Mysql@123' \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/mysql \
  -x mysql-8.0.33-linux-glibc2.12-x86_64.tar.xz \
  -a mysql-8.0.33-linux-glibc2.12-aarch64.tar.xz \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 强制重装

```bash
datasophon-cli init mysql \
  -p 'Mysql@123' \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/mysql \
  -x mysql-8.0.33-linux-glibc2.12-x86_64.tar.xz \
  -a mysql-8.0.33-linux-glibc2.12-aarch64.tar.xz \
  -f \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `MySQL 已安装，跳过` | 已存在 mysqld 进程 | 加 `-f` 强制重装，或直接使用现有实例 |
| `安装包不存在` | tar 包名不匹配 | 确认 `-x` / `-a` 文件名与实际包文件一致 |
| `mysqld 启动失败` | 端口冲突或 libc 版本不足 | 检查 `--mysqlPort` 是否被占用；确认 glibc 版本 |

## 相关命令

- [`init mysql_app_db`](./mysql_app_db.md) — MySQL 安装后创建应用数据库
- [DAG 步骤表](../../../reference/init-all-dag.md)
