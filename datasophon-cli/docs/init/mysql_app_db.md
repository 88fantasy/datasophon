# init mysql_app_db — 创建应用数据库与账号

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitMysqlAppDb.java`

## 用途

在已安装的 MySQL 中，为指定应用创建数据库并配置专属账号（每次调用创建一个账号/数据库对）：

1. `CREATE DATABASE IF NOT EXISTS <dbName>` (utf8mb4/utf8mb4_bin)
2. `CREATE USER '<account>'@'%' IDENTIFIED BY '<password>'`
3. 设置密码永不过期 + 使用 `mysql_native_password` 认证
4. 授权（特殊：`bigdata` 账号只授权自己的库，其他账号授权所有库）

---

## 参数

独有参数 5 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-rootpwd` | `--rootPassword` | **是** | — | MySQL root 密码（用于执行 DDL） |
| `-a` | `--account` | **是** | — | 应用账号名（如 `datasophon`、`hive`） |
| `-p` | `--p` | **是** | — | 应用账号密码 |
| `-d` | `--dbName` | **是** | — | 数据库名（如 `datasophon`） |
| `-mp` | `--mysqlPort` | **是** | — | MySQL 端口（如 `3306`） |

---

## 示例

```bash
# 为 datasophon 应用创建账号和数据库
java -jar datasophon-cli.jar init mysql_app_db \
  -rootpwd <root-password> \
  -a datasophon \
  -p <app-password> \
  -d datasophon \
  -mp 3306

# 为 hive 创建账号和数据库
java -jar datasophon-cli.jar init mysql_app_db \
  -rootpwd <root-password> \
  -a hive \
  -p <app-password> \
  -d hive \
  -mp 3306
```

---

## 行为说明

- 命令先检查 mysqld/mysql 服务是否在 `running` 状态，若未运行则先 `systemctl restart`。
- 所有 SQL 通过 `mysql -uroot -P'<port>' -p'<password>' -e` 命令行执行（非 JDBC）。
- 账号授权策略：
  - `bigdata` 账号：`GRANT ALL PRIVILEGES ON bigdata.* TO 'bigdata'@'%'`（只授权自库）
  - 其他账号：`GRANT ALL PRIVILEGES ON *.* TO '<account>'@'%'`（授权所有库）

---

## 与 cluster-sample.yml 的对应关系

`create cluster initALL` 中，`CreateCluster.initMysqlAppDb()` 遍历 `global.mysql.appDbs[]` 列表，逐个调用此命令：

```yaml
global:
  mysql:
    appDbs:
      - account: "datasophon"
        password: "xxx"
        dbName: "datasophon"
      - account: "hive"
        password: "xxx"
        dbName: "hive"
      # ...
```

单独运行此命令等价于为 yml 中的某一个 `appDbs` 条目单独初始化。

---

## 注意事项

- 若账号已存在，`CREATE USER` 会失败——MySQL 会打印错误但命令不会中断（依赖 MySQL 的错误处理）。建议幂等操作时先手动删除已有账号，或确认账号状态。
- 密码在命令行参数中以明文传递，注意 shell 历史记录泄露风险。
