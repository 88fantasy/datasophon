# init 命令组

`init` 是 datasophon-cli 的节点初始化命令组，包含 25 个可直接调用的原子子命令。每个子命令通常对应一个安装/配置步骤，也可单独在本地调用（`LocalExecutor`），或被 `create cluster` 通过 SSH 远程批量执行。

---

## 公共参数（InitBase）

所有 `init` 子命令均继承 `InitBase`，因此都支持以下 7 个公共参数。**各子命令文档不再重复列出，但可正常传入**。

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-c` | `--config` | 否 | — | `cluster-sample.yml` 配置文件路径。需要读取节点列表的命令（如 `allHost`）必须提供。 |
| `-cpwd` | `--cpassword` | 否 | — | yml 文件的 jasypt 解密密钥。配置文件已加密时必须提供，与 `create cluster -cpwd` 一致。 |
| `-enableR` | `--enableRegistry` | 否 | `false` | 是否通过 Nexus 制品库下载安装包。开启后需同时提供 `-rip/-rport/-rusername/-rpassword`。 |
| `-rip` | `--registryIp` | 否 | — | Nexus 制品库 IP 或 hostname。 |
| `-rport` | `--registryPort` | 否 | — | Nexus Web 端口（对应 `cluster-sample.yml` 中 `global.registry.config.webPort`）。 |
| `-rusername` | `--registryUsername` | 否 | — | Nexus 管理员用户名。 |
| `-rpassword` | `--registryPassword` | 否 | — | Nexus 管理员密码。 |

### 制品库下载说明

当 `-enableR true` 时，支持 Nexus 的子命令会从以下路径下载安装包，而不是从本地 `packagePath` 读取：

```
http://<registryIp>:<registryPort>/repository/raw/packages/<filename>
```

---

## 子命令列表

| 子命令 | 中文名 | 独有参数数 | 文档 |
|---|---|---|---|
| `firewall` | 关闭防火墙 | 0 | [firewall.md](./firewall.md) |
| `selinux` | 关闭 SELinux | 0 | [selinux.md](./selinux.md) |
| `swap` | 关闭 Swap 分区 | 0 | [swap.md](./swap.md) |
| `os` | 初始化 hadoop 用户/组 | 0 | [os.md](./os.md) |
| `system-conf` | 优化系统配置 | 0 | [system-conf.md](./system-conf.md) |
| `hugePage` | 关闭透明大页 | 0 | [hugePage.md](./hugePage.md) |
| `library` | 安装依赖库 | 0 | [library.md](./library.md) |
| `osSafeConf` | OS 基线安全加固 | 0 | [osSafeConf.md](./osSafeConf.md) |
| `bash` | 设置 bash 解析器 | 0 | [bash.md](./bash.md) |
| `nmap` | 安装 nmap | 0 | [nmap.md](./nmap.md) |
| `hostname` | 设置 hostname | 1 | [hostname.md](./hostname.md) |
| `allHost` | 批量写 /etc/hosts | 0（需 `-c`） | [allHost.md](./allHost.md) |
| `ntpserver` | 配置 NTP 服务端 | 0（需 `-c`） | [ntpserver.md](./ntpserver.md) |
| `offlineServer` | 搭建离线 yum/apt 服务 | 3 | [offlineServer.md](./offlineServer.md) |
| `offlineSlave` | 配置节点使用离线源 | 2 | [offlineSlave.md](./offlineSlave.md) |
| `bin_packages` | 分发 datasophon-init 资源包 | 3 | [bin_packages.md](./bin_packages.md) |
| `tar` | 校验 tar 可用 | 1 | [tar.md](./tar.md) |
| `jdk8` | 安装 JDK 8u333 | 1 | [jdk8.md](./jdk8.md) |
| `jdk17` | 安装 OpenJDK 17.0.1 | 1 | [jdk17.md](./jdk17.md) |
| `mysql` | 安装 MySQL 8.0 | 7 | [mysql.md](./mysql.md) |
| `mysql_app_db` | 创建应用数据库与账号 | 5 | [mysql_app_db.md](./mysql_app_db.md) |
| `registry` | 安装 Nexus 制品库 | 11 | [registry.md](./registry.md) |
| `registryUpload` | 上传安装包到 Nexus | 9 | [registryUpload.md](./registryUpload.md) |
| `registryDecode` | 解压/解密制品包 | 5 | [registryDecode.md](./registryDecode.md) |
| `rustfs` | 安装 Rustfs 对象存储 | 10 | [rustfs.md](./rustfs.md) |

---

## 注意事项

- 单独调用 `init` 子命令时，执行目标是**本地机器**（`LocalExecutor`），命令以运行 jar 的当前用户身份执行，通常需要 root 权限。
- `allHost` / `ntpserver` 等需要读取 cluster-sample.yml 节点列表的命令，单独调用时必须显式传 `-c <path>`。
- 无独有参数的命令（如 `firewall`、`selinux`）不需要任何参数即可运行。
