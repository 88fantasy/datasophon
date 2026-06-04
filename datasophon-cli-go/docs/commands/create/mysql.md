# datasophon-cli create mysql

## 用途

在指定节点安装 MySQL 8（解压 rpm/deb 包、初始化数据目录、设置 root 密码、写入 `/etc/my.cnf` 或 `/etc/mysql/mysql.conf.d/mysqld.cnf`、启动 mysqld/mysql 服务）。Datasophon Manager 依赖 MySQL 存储集群元数据。

支持两种模式：

- **配置文件模式**（`-c` 指定配置文件）：从 `cluster-sample.yml` 的 `global.mysql` 读取参数，SSH 到 `mysql.node` 远程执行；安装成功后将 `global.mysql.enable` 回写为 `true`，并依次创建 `mysql.appDbs` 中的所有应用数据库与账号。
- **手动模式**（不指定 `-c`）：所有参数通过命令行传入，在**本地节点**执行；不创建 appDbs，也不写回配置文件。

## 用法 (Synopsis)

```bash
# 配置文件模式
datasophon-cli [--dry-run] create mysql \
  -c <config.yml> --datasophonPath <path> --installPath <path>

# 手动模式
datasophon-cli [--dry-run] create mysql \
  --installPath <path> --node <hostname-or-ip> \
  -f <mysql-bundle-tar> -p <root-password> [--port 3306]
```

## 参数 / Flags

|        flag        |  简写  |   类型   |   默认   |    必填     |                                 说明                                  |
|--------------------|------|--------|--------|-----------|---------------------------------------------------------------------|
| `--config`         | `-c` | string | `""`   | 否         | 配置文件路径；指定后进入配置文件模式                                                  |
| `--datasophonPath` | 无    | string | `""`   | 配置文件模式必填  | datasophon 根目录（推导安装包路径 `<datasophonPath>/datasophon-init/packages`） |
| `--installPath`    | 无    | string | `""`   | 是（两种模式均需） | MySQL 安装根目录（须以 `/` 开头）                                              |
| `--node`           | 无    | string | `""`   | 手动模式必填    | MySQL 节点 hostname 或 IP                                              |
| `--file`           | `-f` | string | `""`   | 手动模式必填    | MySQL tar 安装包完整路径（命令会自动取目录作为 `PackagePath`、文件名作为 X86/AArch64 tar 名） |
| `--password`       | `-p` | string | `""`   | 手动模式必填    | MySQL root 密码                                                       |
| `--port`           | 无    | int    | `3306` | 否         | MySQL 监听端口（手动模式）                                                    |

> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)

## 配置文件依赖（配置文件模式）

|                     字段                     |                                                  说明                                                  |
|--------------------------------------------|------------------------------------------------------------------------------------------------------|
| `global.mysql.node`                        | MySQL 节点 hostname（须在 `nodes` 列表中）                                                                    |
| `global.mysql.password`                    | root 密码                                                                                              |
| `global.mysql.port`                        | MySQL 端口                                                                                             |
| `global.mysql.force`                       | MySQL 已存在时是否强制重装                                                                                     |
| `global.mysql.appDbs[*]`                   | 应用数据库列表（`account` / `password` / `dbName`），逐条调用 [`init mysql_app_db`](../init/db/mysql_app_db.md) 创建 |
| `global.packages.mysql.x86_64` / `aarch64` | MySQL tar 包文件名                                                                                       |
| `global.registry.enable`                   | 为 `true` 时从 Nexus `raw/packages/<file>` 拉取安装包；否则使用本地包                                                |
| `global.registry.config.*`                 | Nexus 凭据（`enable=true` 时使用）                                                                          |
| `global.sshAuthType`                       | SSH 鉴权方式                                                                                             |

## 安装行为差异

|             OS             |                                                            安装方式                                                             |   服务名    |
|----------------------------|-----------------------------------------------------------------------------------------------------------------------------|----------|
| CentOS / openEuler 等 RPM 系 | `yum -y localinstall <bundle>/*.rpm`，需要 `zlib-devel`、`bzip2-devel`、`openssl-devel`、`ncurses-devel` 依赖；CentOS 7 额外装 `libaio` | `mysqld` |
| Ubuntu / Debian 等 deb 系    | `apt localinstall <bundle>/*.rpm -y`，会先清理旧 `mariadb`、`mysql-common` 等冲突包                                                    | `mysql`  |

> 如目标节点已检测到 MySQL 正在运行：
> - 配置文件模式：按 `mysql.force` 决定是否强制重装
> - 手动模式：`Force=false`，跳过安装

## 示例

### 配置文件模式 dry-run

```bash
datasophon-cli --dry-run create mysql \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  --datasophonPath /data/datasophon \
  --installPath /opt/mysql
```

### 配置文件模式实际执行

```bash
datasophon-cli create mysql \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  --datasophonPath /data/datasophon \
  --installPath /opt/mysql
# 成功后：
#   - global.mysql.enable: true 回写
#   - appDbs 中的每个数据库与账号被创建
```

### 手动模式（本地执行）

```bash
datasophon-cli create mysql \
  --installPath /opt/mysql \
  --node 192.168.1.10 \
  -f /data/packages/mysql-8.0.28-1.el8.x86_64.rpm-bundle.tar \
  -p 'Mysql@123' \
  --port 3306
```

## 退出码 / 常见错误

|               错误信息               |                           根因                            |                        处置                        |
|----------------------------------|---------------------------------------------------------|--------------------------------------------------|
| `配置文件模式下 --datasophonPath 为必填项`  | 指定了 `-c` 但未给 `--datasophonPath`                         | 补全 `--datasophonPath`                            |
| `配置中未找到 mysql 节点: <hostname>`    | `global.mysql.node` 不在 `nodes` 列表中                      | 检查 hostname 拼写                                   |
| `配置中未找到 registry 节点: <hostname>` | `registry.enable=true` 但 `registry.node` 不在 `nodes` 列表中 | 关闭制品库模式或修正 registry 节点配置                         |
| `安装包不存在`                         | tar 包路径错误                                               | 确认 `--file`（手动）或 `packages/<mysql.x86_64>`（配置）存在 |
| `MySQL 安装失败`                     | rpm 缺依赖或服务启动失败                                          | 检查 `systemctl status mysqld`；CentOS 注意 libaio    |
| `安装成功但写回配置文件失败`                  | 配置文件写权限不足                                               | 检查 `cluster-sample.yml` 的写权限                     |

## 相关命令

- [`init mysql_app_db`](../init/db/mysql_app_db.md) — 独立创建单个应用数据库（配置文件模式下被自动调用）
- [`create cluster`](./cluster.md) — 集群初始化（DAG 步骤 24/25 复用同一 `mysqlTask`）

