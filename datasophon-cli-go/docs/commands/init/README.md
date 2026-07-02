# init — 单步初始化命令组

`init` 命令组包含 **28 条**独立的初始化子命令，每条对应集群初始化流程中的一个步骤。通常由 `create cluster`（initALL DAG）自动调用；也可单独执行用于故障排查或补跑。

`init` 子命令的设计语义是"在已登录的当前节点上单步本地初始化"。涉及"指定某个特定节点远程安装"的命令（如 MySQL / Nexus / Rustfs / NTP Server / nmap / yum 离线源）已统一迁移到 [`create` 命令组](../create/README.md)，并支持配置文件 / 手动双模式入口。

所有子命令均继承 [init 公共 flag](../../global-flags.md#init-公共-flag)（`-c/--config`、`--registryIp` 等）。

## 子命令速查

### system — 操作系统基础配置（9 条）

|                   命令                   |               说明               |
|----------------------------------------|--------------------------------|
| [firewall](./system/firewall.md)       | 关闭防火墙（firewalld / ufw）         |
| [selinux](./system/selinux.md)         | 关闭 SELinux                     |
| [swap](./system/swap.md)               | 关闭 Swap 分区                     |
| [hadoop_user](./system/osuser.md)      | 创建 hadoop 用户和组                 |
| [bash](./system/bash.md)               | 确保 /bin/sh 指向 bash             |
| [library](./system/library.md)         | 安装运行时依赖库                       |
| [osSafeConf](./system/ossafeconf.md)   | 初始化基线安全配置                      |
| [system-conf](./system/system-conf.md) | 设置系统配置（limits/sysctl/rc-local） |
| [hugePage](./system/hugepage.md)       | 关闭透明大页                         |

### network — 网络与时间同步（4 条）

|                命令                 |         说明          |
|-----------------------------------|---------------------|
| [hostname](./network/hostname.md) | 配置主机名               |
| [allHost](./network/allhost.md)   | 初始化 /etc/hosts（全节点） |
| [ntpslave](./network/ntpslave.md) | 配置 chrony NTP 从节点   |
| [ssh](./network/ssh.md)           | 配置 SSH 免密登录         |

> NTP 服务端与 nmap 安装请使用 [`create ntp-server`](../create/ntp-server.md) / [`create nmap-server`](../create/nmap-server.md)。

### packages — 基础软件包（5 条）

|                     命令                     |                  说明                  |
|--------------------------------------------|--------------------------------------|
| [bin_packages](./packages/bin_packages.md) | 分发 datasophon-init 二进制目录             |
| [tar](./packages/tar.md)                   | 确认 tar 命令存在                          |
| [jdk8](./packages/jdk8.md)                 | 安装 JDK 8（`/usr/local/jdk8/`） |
| [jdk17](./packages/jdk17.md)               | 安装 JDK 17（`/usr/local/jdk17/`，K8s 场景遗留） |
| [jdk21](./packages/jdk21.md)               | 安装 JDK 21（`/usr/local/jdk21/`，Datasophon Manager 平台运行时） |

### repo — 制品库与离线源（2 条）

|                     命令                     |         说明         |
|--------------------------------------------|--------------------|
| [offlineSlave](./repo/offlineslave.md)     | 配置节点使用离线 yum/apt 源 |
| [registryDecode](./repo/registrydecode.md) | 将离线包解码并导入到 Nexus   |

> Nexus 制品库安装请使用 [`create registry`](../create/registry.md)；离线源服务端安装请使用 [`create yum-server`](../create/yum-server.md)；Rustfs 安装请使用 [`create rustfs`](../create/rustfs.md)。

### db — 数据库初始化（1 条）

|                  命令                  |           说明            |
|--------------------------------------|-------------------------|
| [mysql_app_db](./db/mysql_app_db.md) | 在已安装的 MySQL 上创建应用数据库及账号 |

> MySQL 自身安装请使用 [`create mysql`](../create/mysql.md)；配置文件模式下会自动依次调用 `mysql_app_db` 创建 `appDbs` 列表中的每个数据库。

### k8s — Kubernetes 生态（7 条）

|                     命令                      |            说明             |
|---------------------------------------------|---------------------------|
| [docker](./k8s/docker.md)                   | 安装 Docker（containerd 模式）  |
| [helm](./k8s/helm.md)                       | 安装 Helm                   |
| [helmify](./k8s/helmify.md)                 | 安装 Helmify                |
| [kubectl](./k8s/kubectl.md)                 | 安装 kubectl                |
| [k8sBaseServices](./k8s/k8sbaseservices.md) | 用 sealos 部署 Kubernetes 集群 |
| [k8sRegistryConf](./k8s/k8sregistryconf.md) | 配置 containerd 私有镜像仓库认证    |
| [kuboard](./k8s/kuboard.md)                 | 用 sealos 安装 Kuboard       |

## 用法

```bash
datasophon-cli [--dry-run] init <subcommand> [flags]
```

## 参考

- [initALL / standalone DAG 步骤表](../../reference/init-all-dag.md)
- [init 公共 flag 说明](../../global-flags.md#init-公共-flag)
- [`create` 命令组](../create/README.md) — MySQL/Nexus/Rustfs/NTP/Nmap/Yum 等远程安装命令

