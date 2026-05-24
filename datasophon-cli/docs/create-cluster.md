# create cluster — 集群编排命令

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/create/CreateCluster.java`

## 用途

`create cluster` 是 datasophon-cli 的**编排入口**：读取 `cluster-sample.yml` 获取集群拓扑，通过 jsch SSH 对各节点批量执行初始化步骤，完成整个集群的一次性初始化（`initALL`）或扩容新增节点（`initSingleNode`）。

---

## 参数

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-p` | `--datasophonPath` | **是** | — | Datasophon 主目录绝对路径（须以 `/` 开头）。CLI 自动从此路径派生 `datasophon-init/` 子目录。 |
| `-in` | `--installPath` | **是** | — | 远程节点安装目标路径绝对路径（如 `/opt/datasophon`）。 |
| `-cpwd` | `--cpassword` | **是** | — | `cluster-sample.yml` 的 jasypt 解密密钥（明文 yml 也须传此参数，内部会先判断是否加密再决定是否解密）。 |
| `-a` | `--action` | **是** | — | 编排动作：`initALL`（首次全量初始化）或 `initSingleNode`（扩容新增节点）。 |
| `-pn` | `--productPackagesPath` | **是** | — | 产品安装包目录绝对路径（用于 `registryUpload` 上传到 Nexus）。 |
| `-if` | `--initPathOverwriteForce` | 否 | `false` | `datasophon-init` 目录在远程已存在时是否强制覆盖分发。 |
| `-disu` | `--disableUploadRegistry` | 否 | `false` | 禁止上传制品到 Nexus（只安装 Nexus，跳过上传步骤）。 |
| `-f` | `--mysqlInstallForce` | 否 | `false` | MySQL 已安装时是否强制重装。 |
| `-e` | `--enableRegistry` | 否 | `false` | 是否启用 Nexus 制品库（`true` 时安装 Rustfs + Nexus + 上传包）。 |
| `-oik` | `--onlyInstallK8s` | 否 | `false` | `true` 时跳过所有步骤，仅安装 Kubernetes 集群。 |
| `-kf` | `--kubernetesForce` | 否 | `false` | K8s 已安装时是否强制重装（同时传给 Docker 安装）。 |

---

## 自动派生路径

| 变量 | 派生规则 |
|---|---|
| `initPath` | `<datasophonPath>/datasophon-init/` |
| `initConfigPath` | `<datasophonPath>/datasophon-init/config/` |
| `initConfigYamlPath` | `<datasophonPath>/datasophon-init/config/cluster-sample.yml` |
| `packagesPath` | `<datasophonPath>/datasophon-init/packages/` |

---

## SSH 认证

认证方式由 `cluster-sample.yml` 中 `global.sshAuthType` 控制：

| 值 | 说明 |
|---|---|
| `AUTO` | 自动选择（密钥优先，无密钥时用密码） |
| `PASSWORD` | 明文密码登录 |
| `KEY` | SSH 私钥登录 |

---

## 典型用法

### 场景一：首次全量初始化（含制品库）

```bash
export DDH_HOME=/opt/datasophon

java -jar datasophon-cli.jar create cluster \
  -p /opt/datasophon \
  -in /opt/datasophon \
  -cpwd <your-decrypt-key> \
  -a initALL \
  -pn /mnt/packages/product \
  -e true
```

### 场景二：首次全量初始化（不用制品库，直接离线包）

```bash
java -jar datasophon-cli.jar create cluster \
  -p /opt/datasophon \
  -in /opt/datasophon \
  -cpwd <your-decrypt-key> \
  -a initALL \
  -pn /mnt/packages/product
```

### 场景三：仅安装 K8s 集群

```bash
java -jar datasophon-cli.jar create cluster \
  -p /opt/datasophon \
  -in /opt/datasophon \
  -cpwd <your-decrypt-key> \
  -a initALL \
  -pn /mnt/packages/product \
  -oik true
```

### 场景四：扩容新增节点

> 前提：已在 `cluster-sample.yml` 的 `addNodes[]` 中添加了新节点信息。

```bash
java -jar datasophon-cli.jar create cluster \
  -p /opt/datasophon \
  -in /opt/datasophon \
  -cpwd <your-decrypt-key> \
  -a initSingleNode \
  -pn /mnt/packages/product
```

---

## 编排步骤详解

### initALL（全量初始化，28 步）

| 步骤 | 内容 | 目标节点 | 条件 |
|---|---|---|---|
| 1 | 分发 datasophon-init 资源包（`bin_packages`） | 所有非本地 Worker | 始终 |
| 2 | 设置 bash 解析器（`bash`） | 所有节点 | 始终 |
| 3 | 校验 tar（`tar`） | 所有非本地 Worker | 始终 |
| 4 | 安装 Rustfs（`rustfs`） | `global.rustfs.nodes[0]` | `-e true` |
| 5 | 安装 Nexus 制品库（`registry`） | `global.registry.node` | `-e true` |
| 6 | 安装 Docker（内部命令） | 本地 Master | `-e true` + k8s 启用 |
| 7 | 上传安装包到 Nexus（`registryUpload`） | 本地 Master | `-e true` |
| 8 | 安装 JDK 8（`jdk8`） | 所有非本地 Worker | 始终 |
| 9 | 安装 JDK 17（`jdk17`） | 所有非本地 Worker | 始终 |
| 10 | 创建 hadoop 用户/组（`os`） | 所有节点 | 始终 |
| 11 | 关闭防火墙（`firewall`） | 所有节点 | 始终 |
| 12 | 关闭 SELinux（`selinux`） | 所有节点 | 始终 |
| 13 | 关闭 Swap（`swap`） | 所有节点 | 始终 |
| 14 | 搭建离线 yum/apt 源（`offlineServer`） | `global.yumServer.node` | 始终 |
| 15 | 配置节点使用离线源（`offlineSlave`） | 所有节点 | 始终 |
| 16 | 安装依赖库（`library`） | 所有节点 | 始终 |
| 17 | OS 基线安全加固（`osSafeConf`） | 所有节点 | 始终 |
| 18 | 优化系统配置（`system-conf`） | 所有节点 | 始终 |
| 19 | 设置 hostname（`hostname`） | 每个节点各自 | 始终 |
| 20 | 批量写入 /etc/hosts（`allHost`） | 每个节点 | 始终 |
| 21 | 安装 nmap（`nmap`） | `global.nmapServer.node` | 始终 |
| 22 | 配置 NTP 服务端（`ntpserver`） | `global.ntpServer.node` | 始终 |
| 23 | 配置 NTP 客户端（内部：`ntpslave`） | 所有非 NTP 服务端节点 | 始终 |
| 24 | 安装 MySQL（`mysql`） | `global.mysql.node` | 始终 |
| 25 | 创建应用数据库（`mysql_app_db`） | `global.mysql.node` | 始终（遍历 appDbs） |
| 26 | 安装 K8s 集群（内部：多个 K8s 命令） | K8s masters + nodes | `global.kubernetes.enable=true` |
| 27 | 关闭透明大页（`hugePage`） | 所有节点 | 始终 |

> `-oik true` 时直接跳到步骤 26，仅执行 K8s 安装。

### initSingleNode（新增节点，13 步）

仅对 `cluster-sample.yml` 中 `addNodes[]` 列表的节点执行，跳过制品库、MySQL、K8s、nmap、NTP 服务端等集群单例步骤：

| 步骤 | 内容 |
|---|---|
| 1 | 分发资源包（`bin_packages`） |
| 2 | 设置 bash |
| 3 | 校验 tar |
| 4 | 安装 JDK 8 |
| 5 | 安装 JDK 17 |
| 6 | 创建 hadoop 用户/组 |
| 7 | 关闭防火墙 |
| 8 | 关闭 SELinux |
| 9 | 关闭 Swap |
| 10 | 配置离线源（`offlineSlave`） |
| 11 | 安装依赖库 |
| 12 | OS 基线安全加固 |
| 13 | 优化系统配置 |
| 14 | 设置 hostname |
| 15 | 批量写入 /etc/hosts（原有节点 + 新节点各跑一遍） |
| 16 | 配置 NTP 客户端 |
| 17 | 关闭透明大页 |

---

## 注意事项

- `datasophonPath` 和 `installPath` 均**必须是绝对路径**（以 `/` 开头），否则命令启动即报错。
- 命令从 yml 第一个节点（`nodes[0]`）的 IP 与本机 IP 对比，确定"本地 Master 节点"——确保运行此命令的机器 IP 在 yml 的 `nodes[]` 中。
- 整个编排是**同步串行**的：一个节点上的某步骤失败，后续步骤不执行（该节点停止）；但不同节点间没有依赖，任何节点失败不影响其他节点。
- 每个节点每个步骤通过独立 SSH Session 执行，步骤完成后关闭 Session（不复用）。
