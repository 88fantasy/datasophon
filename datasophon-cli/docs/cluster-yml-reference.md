# cluster-sample.yml 字段说明

`cluster-sample.yml` 是 `create cluster` 命令的核心配置文件，描述集群节点拓扑和全局服务配置。默认路径：

```
<datasophonPath>/datasophon-init/config/cluster-sample.yml
```

---

## 加密说明

`cluster-sample.yml` 支持两种加密形态：

### 整体加密（Base64 + Jasypt）

整个 yml 文件被 Jasypt 加密后 Base64 编码，CLI 启动时自动检测（`Base64.isBase64(content)`），解密后内存中使用：

```bash
# 使用 registryDecode 命令解密并释放到工作目录
java -jar datasophon-cli.jar init registryDecode \
  -e true -de true -cpwd <decrypt-key> ...
```

### 字段级加密（Jasypt ENC）

单个敏感字段值（如密码）为 Jasypt 密文，特征是值末尾有 `==`：

```yaml
password: "u4Gkp19TRcKKlTCLNA1pyA=="
```

通过 `CliUtil.getConfig(path, password)` 读取时传入 `-cpwd` 密钥解密。

---

## 顶层结构

```yaml
global:        # 全局服务配置
  ...
nodes:         # 集群节点列表（已有节点）
  - ...
addNodes:      # 新增节点列表（仅 initSingleNode 时使用）
  - ...
```

---

## global.osInfo — 操作系统信息

```yaml
global:
  offline: true          # 是否离线环境（影响部分下载逻辑）
  osInfo:
    auto: true           # 自动检测 OS 类型（true 时忽略 osType/archType 手动配置）
    osType: openEuler-22.03-LTS-SP3  # 手动指定 OS 类型（auto=false 时生效）
    archType: x86_64     # 手动指定 CPU 架构（auto=false 时生效）
```

---

## global.sshAuthType — SSH 认证方式

```yaml
global:
  sshAuthType: "AUTO"    # AUTO | PASSWORD | KEY
```

| 值 | 说明 |
|---|---|
| `AUTO` | 自动选择：优先尝试 SSH 密钥，失败后使用密码 |
| `PASSWORD` | 使用 nodes[].password 密码登录 |
| `KEY` | 使用 SSH 私钥登录（私钥路径需另行配置） |

---

## global.registry — Nexus 制品库

```yaml
global:
  registry:
    enable: true           # 是否启用 Nexus 制品库（对应 create cluster -e）
    type: "nexus"          # 制品库类型（当前只支持 nexus）
    config:
      webPort: 8091        # Nexus Web 端口
      user: "admin"        # Nexus 管理员用户名
      password: "..."      # Nexus 管理员密码（可 Jasypt 加密）
      dockerHttpPort: 8083 # Docker 仓库 HTTP 端口
      repositories:        # 初始化的仓库列表
        - "yum"
        - "raw"
        - "apt"
        - "docker"
        - "helm"
    node: "app6"           # 安装 Nexus 的节点 hostname
```

---

## global.yumServer — 离线 yum/apt HTTP 源

```yaml
global:
  yumServer:
    enable: true           # 是否启用离线源服务（未启用 Nexus 时使用 httpd）
    node: "app6"           # 离线源服务器节点 hostname
    listenPort: 4080       # httpd 监听端口
```

---

## global.nmapServer — nmap 安装节点

```yaml
global:
  nmapServer:
    enable: true
    node: "app6"           # 安装 nmap 的节点 hostname
```

---

## global.mysql — MySQL 配置

```yaml
global:
  mysql:
    enable: true
    user: "root"           # MySQL root 用户名（当前固定 root，未实际使用）
    password: "..."        # MySQL root 密码（可 Jasypt 加密）
    port: 3306             # MySQL 监听端口
    node: "app6"           # 安装 MySQL 的节点 hostname
    appDbs:                # 应用数据库列表（create cluster 逐一创建）
      - account: "datasophon"
        password: "..."
        dbName: "datasophon"
      - account: "hive"
        password: "..."
        dbName: "hive"
      # 支持的应用：hive / dolphinscheduler / ustream / amoro /
      #             nacos / bigdata / research / operation / juicefs /
      #             portal / datart 等（按需添加）
```

---

## global.ntpServer — NTP 服务端

```yaml
global:
  ntpServer:
    enable: true
    node: "app6"           # NTP 服务端节点 hostname（其他节点同步到此）
```

---

## global.rustfs — Rustfs 对象存储

```yaml
global:
  rustfs:
    enable: true
    config:
      webPort: 9041        # Web 控制台端口
      apiPort: 9040        # S3 API 端口
      user: "admin"        # AccessKey
      password: "..."      # SecretKey（可 Jasypt 加密）
      installType: "SNSD"  # SNSD(单节点单盘) | SNMD(单节点多磁盘) | MNMD(多节点多磁盘)
      volumes: "/data/rustfs0"  # 数据卷路径（MNMD 示例：http://node{1...4}:9040/data/rustfs{0...3}）
    nodes: ["app6"]        # Rustfs 节点列表（当前只取 nodes[0]）
```

---

## global.kubernetes — Kubernetes 集群

```yaml
global:
  kubernetes:
    enable: true
    baseServices:
      namespaces: ["prod"]          # K8s 命名空间列表
      masters: ["app2"]             # K8s Master 节点 hostname 列表
      nodes: ["app3", "app4", "app6"]  # K8s Worker 节点 hostname 列表
      sealos: true                  # 是否使用 Sealos 部署
      kubernetesI: true             # 是否安装 Kubernetes 本体
      helmI: true                   # 是否安装 Helm（K8s 内）
      calicoI: true                 # 是否安装 Calico CNI
      ingressI: true                # 是否安装 Nginx Ingress
    kuboardI:
      enable: true
      node: "app2"                  # 安装 Kuboard 的节点 hostname
      etcdNodes: ["app3", "app4", "app6"]  # Kuboard etcd 节点（至少 3 个）
    k8sTools:
      docker: true                  # 本地 Master 是否安装 Docker
      helm: true                    # 本地 Master 是否安装 Helm CLI
      helmify: true                 # 本地 Master 是否安装 Helmify
      kubectl: true                 # 本地 Master 是否安装 kubectl
```

---

## global.packages — 安装包文件名映射

各组件的安装包文件名，按 CPU 架构区分（x86_64 / aarch64）：

```yaml
global:
  packages:
    os: "openEuler-22.03-LTS-SP3.tar.gz"   # OS 软件包（离线源解压包）
    config: "config.tar.gz"                  # 配置包
    soft: "packages.tar.gz"                  # 软件包总包
    nexus:
      x86_64: "nexus-3.85.0-03-linux-x86_64.tar.gz"
      aarch64: "nexus-3.85.0-03-linux-aarch_64.tar.gz"
    mysql:
      x86_64: "mysql-8.0.28-1.el8.x86_64.rpm-bundle.tar"
      aarch64: "mysql-8.0.28-1.el8.aarch64.rpm-bundle.tar"
    rustfs:
      x86_64: "rustfs-linux-x86_64-musl-1.0.0.tar.gz"
      aarch64: "rustfs-linux-aarch64-musl-1.0.0.tar.gz"
    sealos:
      x86_64: "sealos_5.1.0_linux_amd64.tar.gz"
      aarch64: "sealos_5.1.0_linux_arm64.tar.gz"
    kubernetesI:
      x86_64: "kubernetes-v1.31.8-x86.tar"
      aarch64: "kubernetes-v1.31.8-arm.tar"
    helmI:
      x86_64: "helm-v4.0.1-x86.tar"
      aarch64: "helm-v4.0.1-arm.tar"
    calicoI:
      x86_64: "calico-v3.28.1-x86.tar"
      aarch64: "calico-v3.28.1-arm.tar"
    ingressI:
      x86_64: "ingress-nginx-4.1.0-x86.tar"
      aarch64: "ingress-nginx-4.1.0-arm.tar"
    kuboardI:
      x86_64: "kuboard-v3-x86.tar"
      aarch64: "kuboard-v3-arm.tar"
    helmify:
      x86_64: "helmify_Linux_arm64.tar.gz"   # 注意：样例 yml 中 x86/arm 似乎对调
      aarch64: "helmify_Linux_x86_64.tar.gz"
    docker:
      x86_64: "docker-x86-29.3.1.tgz"
      aarch64: "docker-arm-29.3.1.tgz"
    helm:
      x86_64: "helm-v4.0.1-linux-amd64.tar.gz"
      aarch64: "helm-v4.0.1-linux-arm64.tar.gz"
    kubectl:
      x86_64: "kubectl-x86"
      aarch64: "kubectl-arm"
```

> **注意**：`packages.helmify` 中 x86_64 和 aarch64 的文件名在样例 yml 中似乎互换了（x86_64 对应 arm64 文件），使用前请核实。

---

## nodes — 节点列表

```yaml
nodes:
  - projectEnvDetailId: 2001478085436047360  # 可选，Datasophon 平台的节点 ID
    ip: "192.168.2.213"     # 节点 IP（必填）
    user: "root"            # SSH 用户名（必填）
    password: "..."         # SSH 密码（必填，可 Jasypt 加密）
    port: 22                # SSH 端口（必填，通常为 22）
    hostname: "app2"        # 节点 hostname（必填，与 global 中各 node 字段对应）
```

**节点字段说明**：

| 字段 | 必填 | 说明 |
|---|---|---|
| `ip` | **是** | IPv4 地址，用于 SSH 连接 |
| `user` | **是** | SSH 登录用户（通常为 `root`） |
| `password` | **是** | SSH 密码（`sshAuthType=KEY` 时可置空） |
| `port` | **是** | SSH 端口 |
| `hostname` | **是** | 节点 hostname，与 `global.*Server.node` 等字段引用对应 |
| `projectEnvDetailId` | 否 | Datasophon 平台分配的节点唯一 ID，初始化时不影响 CLI 行为 |

---

## addNodes — 扩容节点列表

结构与 `nodes[]` 完全相同，在 `create cluster -a initSingleNode` 时作为扩容目标。

```yaml
addNodes:
  - ip: "192.168.2.240"
    user: "root"
    password: "..."
    port: 22
    hostname: "app7"
```

> 扩容时 `allHost` 命令会将 `addNodes[]` 中的条目同时写入**原有节点**和**新节点**的 `/etc/hosts`。

---

## 完整示例

参见源码中的样例文件：`datasophon-cli/src/main/resources/cluster-sample.yml`
