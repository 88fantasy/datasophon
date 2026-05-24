# init mysql — 安装 MySQL 8.0

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitMysql.java`

## 用途

在目标节点上安装 MySQL 8.0，配置字符集、连接数、端口，并设置 root 用户允许远程连接。

---

## 参数

独有参数 7 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-p` | `--password` | **是** | — | MySQL root 密码（安装后设置） |
| `-f` | `--force` | 否 | `false` | MySQL 已安装时是否强制重装（`true`=卸载重装，`false`=跳过） |
| `-pp` | `--packagePath` | **是** | — | 安装包根目录 |
| `-in` | `--installPath` | **是** | — | 安装临时工作目录（rpm 解压到 `<installPath>/tmp/mysql/`） |
| `-x` | `--x86Tar` | **是** | — | x86_64 RPM 包文件名（如 `mysql-8.0.28-1.el8.x86_64.rpm-bundle.tar`） |
| `-a` | `--aarch64Tar` | **是** | — | aarch64 RPM 包文件名（如 `mysql-8.0.28-1.el8.aarch64.rpm-bundle.tar`） |
| `-mp` | `--mysqlPort` | **是** | — | MySQL 监听端口（如 `3306`） |

---

## 示例

```bash
java -jar datasophon-cli.jar init mysql \
  -p <your-mysql-root-password> \
  -pp /opt/datasophon/datasophon-init/packages \
  -in /opt/datasophon \
  -x mysql-8.0.28-1.el8.x86_64.rpm-bundle.tar \
  -a mysql-8.0.28-1.el8.aarch64.rpm-bundle.tar \
  -mp 3306
```

### 已安装时强制重装

```bash
java -jar datasophon-cli.jar init mysql \
  -p <your-mysql-root-password> \
  -pp /opt/datasophon/datasophon-init/packages \
  -in /opt/datasophon \
  -x mysql-8.0.28-1.el8.x86_64.rpm-bundle.tar \
  -a mysql-8.0.28-1.el8.aarch64.rpm-bundle.tar \
  -mp 3306 \
  -f true
```

---

## 行为说明

### 安装前检查

检查 `systemctl status mysqld`（Ubuntu: `mysql`）：
- 已运行且 `-f=false`：直接返回成功（跳过）。
- 已运行且 `-f=true`：卸载现有 MySQL（包含卸载 mariadb、清理数据目录）。

### 安装步骤（CentOS）

1. 卸载 mariadb（若存在）
2. 卸载已有 mysql（若存在）
3. 安装依赖：`zlib-devel / bzip2-devel / openssl-devel / ncurses-devel`（CentOS 7 额外装 libaio）
4. 解压 tar 包 → `yum -y localinstall <installPath>/tmp/mysql/*.rpm`
5. `mysqld --initialize --user=mysql`
6. 启动 mysqld，获取临时密码
7. `mysqladmin` 修改 root 密码
8. 配置 root 用户允许远程连接（`host='%'`）
9. 备份并覆写 `/etc/my.cnf`（charset=utf8mb4, max_connections=3600）

### 安装后的 my.cnf 关键配置

```ini
[mysqld]
character_set_server=utf8mb4
collation_server=utf8mb4_bin
max_connections=3600
port=<mysqlPort>
sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,...
```

---

## 注意事项

- 安装包文件名与 yml `global.packages.mysql.x86_64` 字段对应，单独调用时需手动填写正确文件名。
- 启用 Nexus（`-enableR true`）时，安装包从制品库的 `raw` 仓库下载；否则从 `-pp` 本地目录读取。
- `create cluster` 编排中只在 `global.mysql.node` 指定的**单个节点**执行 mysql 安装。
- Ubuntu 安装使用 `apt localinstall`（即 `dpkg -i`），实际代码写的是 `apt localinstall`，可能存在兼容问题——建议在 Ubuntu 上优先验证。
