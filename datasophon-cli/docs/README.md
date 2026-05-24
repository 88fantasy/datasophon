# datasophon-cli 使用说明书

`datasophon-cli` 是 Datasophon 的节点初始化命令行工具，基于 [picocli 4.7.6](https://picocli.info/) 实现。它负责在目标节点上完成 OS 基线加固、基础软件安装，以及通过读取 `cluster-sample.yml` 配置文件编排整个集群的一次性初始化或新增节点扩容。

---

## 前置条件

| 条件 | 说明 |
|---|---|
| `DDH_HOME` 环境变量 | **必须设置**，否则 CLI 启动时直接退出。示例：`export DDH_HOME=/opt/datasophon` |
| Java 8+ | 运行 jar 需要 Java 运行时，推荐与目标环境一致 |
| SSH root 权限 | `create cluster` 通过 jsch SSH 远程执行，目标节点需要 root 账号 |
| `tar` 命令 | 目标节点必须预装 `tar`（`init tar` 会校验） |

---

## JAR 位置

```bash
datasophon-cli/target/datasophon-cli.jar
```

构建命令（需要 JDK 17 + Maven Wrapper）：

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jbr-17.0.12-1/Contents/Home
./mvnw clean package -DskipTests -s ~/.m2/setting.xml -pl datasophon-cli -am
```

---

## 调用范式

```
java -jar datasophon-cli.jar <一级命令> <二级命令> [选项...]
```

查看任意命令帮助：

```bash
java -jar datasophon-cli.jar --help
java -jar datasophon-cli.jar init --help
java -jar datasophon-cli.jar init mysql --help
```

---

## 命令树

```
datasophon-cli
├─ create
│   └─ cluster                          # 编排命令：读取 cluster-sample.yml 初始化整个集群
└─ init                                 # 原子初始化命令（25 个可直接调用）
    ├─ [系统基础]
    │   ├─ firewall                     # 关闭防火墙（firewalld / ufw）
    │   ├─ selinux                      # 关闭 SELinux
    │   ├─ swap                         # 关闭 Swap 分区
    │   ├─ os                           # 创建 hadoop 用户与组
    │   ├─ system-conf                  # 优化系统文件描述符 / sysctl 限制
    │   ├─ hugePage                     # 关闭透明大页
    │   ├─ library                      # 安装依赖库（psmisc / perl-JSON / openssl 等）
    │   ├─ osSafeConf                   # OS 基线安全加固
    │   └─ bash                         # 设置 /bin/sh → bash
    ├─ [监控与工具]
    │   └─ nmap                         # 安装 nmap
    ├─ [主机标识]
    │   ├─ hostname                     # 设置节点 hostname
    │   └─ allHost                      # 批量写入 /etc/hosts（读取 cluster-sample.yml）
    ├─ [时间同步]
    │   └─ ntpserver                    # 配置 NTP 服务端（chrony）
    ├─ [离线源]
    │   ├─ offlineServer                # 搭建 yum/apt 离线 HTTP 源服务器
    │   └─ offlineSlave                 # 配置节点使用离线源
    ├─ [资源分发]
    │   ├─ bin_packages                 # 分发 datasophon-init 资源包到远程节点
    │   └─ tar                          # 校验 tar 命令可用
    ├─ [JDK]
    │   ├─ jdk8                         # 安装 JDK 8u333
    │   └─ jdk17                        # 安装 OpenJDK 17.0.1
    ├─ [MySQL]
    │   ├─ mysql                        # 安装 MySQL 8.0
    │   └─ mysql_app_db                 # 创建应用数据库与账号
    └─ [制品库]
        ├─ registry                     # 安装 Nexus 制品库（含 yum/apt/raw/docker/helm）
        ├─ registryUpload               # 上传安装包到 Nexus
        ├─ registryDecode               # 解压/解密制品包
        └─ rustfs                       # 安装 Rustfs 对象存储（S3 兼容）
```

> **内部命令**：`InitDocker / InitHelm / InitHelmify / InitKubectl / InitK8sBaseServices / InitK8sKuboard / InitK8sRegistryConf / InitNtpSlave / InitSsh` 这 9 个类仅供 `create cluster` 内部调用，**未注册到 `init` 子命令列表**，直接 `init xxx` 会报错。详见 [internal-commands.md](./internal-commands.md)。

---

## 执行模式

| 模式 | 场景 | 实现 |
|---|---|---|
| **本地直跑** | 单独 `java -jar ... init mysql` | `LocalExecutor`，在当前机器执行 shell |
| **SSH 远程批跑** | `create cluster` 编排 | `JschExecutor`（基于 jsch）+ `InitNodeHandlerChain` 链式执行 |

---

## 文档索引

| 文档 | 内容 |
|---|---|
| [create-cluster.md](./create-cluster.md) | `create cluster` 编排命令完整参数与编排步骤 |
| [init/README.md](./init/README.md) | `init` 命令组概述 + `InitBase` 公共参数说明 |
| [init/firewall.md](./init/firewall.md) ～ [init/rustfs.md](./init/rustfs.md) | 25 个 init 子命令各自的参数与示例 |
| [cluster-yml-reference.md](./cluster-yml-reference.md) | `cluster-sample.yml` 所有字段说明 |
| [internal-commands.md](./internal-commands.md) | 9 个内部命令说明（仅供阅读源码参考） |
