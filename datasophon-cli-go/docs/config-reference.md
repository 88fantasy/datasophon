# 配置文件参考（cluster-sample.yml）

集群配置文件是 `datasophon-cli create cluster` 的唯一配置源。默认路径：

```
<datasophonPath>/datasophon-init/config/cluster-sample.yml
```

可用 [`create config`](./commands/create/config.md) 生成带随机密码的初始模板，然后按本文档修改各字段。

---

## 顶层结构

```yaml
global:            # cluster-type / offline / osInfo / sshAuthType
registry:          # Nexus
rustfs:            # S3 兼容对象存储
baseOtelCollector: # 引导期基础设施监控
mysql:             # MySQL
kubernetes:        # Kubernetes（按集群类型可选）
packages:          # 双架构制品名
nodes:             # 集群节点列表（至少 1 个，含主节点）
```

> 早期版本支持的顶层 `type:` 和 `addNodes:` 字段均已移除。集群类型改为 `global.cluster-type`；扩容通过 `create node --ip ...` 对单节点初始化，成功后由命令自动追加到 `nodes` 列表。

---

## global

### global.cluster-type

|       字段       |   类型   | 默认 | 必填 |                                              说明                                               |
|----------------|--------|----|----|-----------------------------------------------------------------------------------------------|
| `cluster-type` | string | —  | 是  | 集群类型。`hadoop`：Hadoop 大数据集群（含 osuser 步骤，跳过 k8s-*）；`kubernetes`：K8s 集群（包含所有 k8s-* 步骤，跳过 osuser） |

`cluster-type` 也可通过 `--type` / `-t` CLI flag 覆盖（flag 优先于文件中的值）。两者均需为 `hadoop` 或 `kubernetes`，否则命令启动时报错。

```yaml
global:
  cluster-type: hadoop   # 或 kubernetes
```

---

### global.offline

|    字段     |  类型  |   默认    |                说明                |
|-----------|------|---------|----------------------------------|
| `offline` | bool | `false` | `true` 表示离线环境，所有包从本地或制品库获取，不访问公网 |

---

### global.osInfo

系统类型与架构信息，影响包管理器选择（yum/apt）和安装包文件名。

|     字段     |   类型   |   默认   |                               说明                                |
|------------|--------|--------|-----------------------------------------------------------------|
| `auto`     | bool   | `true` | `true` 则自动探测（推荐）；`false` 则使用下方手动指定的值                            |
| `osType`   | string | `""`   | 手动指定 OS 类型，如 `openEuler-22.03-LTS-SP3`、`centos7`、`ubuntu-22.04` |
| `archType` | string | `""`   | 手动指定架构：`x86_64` 或 `aarch64`                                     |

---

### global.sshAuthType

|      字段       |   类型   |                可选值                |   默认   |
|---------------|--------|-----------------------------------|--------|
| `sshAuthType` | string | `AUTO` / `PASSWORD` / `PUBLICKEY` | `AUTO` |

详见 [全局选项 → SSH 鉴权](./global-flags.md#ssh-鉴权)。

---

### registry

控制 Nexus 制品库的安装与使用。`enable: true` 时，DAG 中会激活 `init-registry`、`init-registry-upload`、`init-offline-nodes` 等步骤。

|           字段            |    类型    |                  默认                   |                  说明                   |
|-------------------------|----------|---------------------------------------|---------------------------------------|
| `enable`                | bool     | `false`                               | 是否启用制品库                               |
| `disableUpload`         | bool     | `false`                               | `true` 时跳过上传步骤（制品库已有包时使用）             |
| `type`                  | string   | `"nexus"`                             | 制品库类型，目前仅支持 `nexus`                   |
| `node`                  | string   | 必填                                    | 运行 Nexus 的节点 hostname（须在 `nodes` 列表中） |
| `config.webPort`        | int      | `8091`                                | Nexus Web UI 端口                       |
| `config.user`           | string   | `"admin"`                             | Nexus 管理员用户名                          |
| `config.password`       | string   | 必填                                    | Nexus 管理员密码（`create config` 自动随机生成）   |
| `config.dockerHttpPort` | int      | `8083`                                | Docker 镜像仓库 HTTP 端口（K8s 场景需要）         |
| `config.repositories`   | []string | `["yum","raw","apt","docker","helm"]` | 要创建的仓库类型列表                            |

**相关 DAG 步骤**：`init-registry`（步骤 5）、`init-registry-upload`（步骤 7）、`init-offline-nodes`（步骤 15）

---

### rustfs

对象存储服务（Rustfs，兼容 S3 协议）。

|          字段          |    类型    |        默认         |                                                    说明                                                     |
|----------------------|----------|-------------------|-----------------------------------------------------------------------------------------------------------|
| `enable`             | bool     | `false`           | 是否安装 Rustfs                                                                                               |
| `nodes`              | []string | `[]`              | 运行 Rustfs 的节点 hostname 列表                                                                                 |
| `config.webPort`     | int      | `9041`            | Web 管理端口                                                                                                  |
| `config.apiPort`     | int      | `9040`            | S3 API 端口                                                                                                 |
| `config.user`        | string   | `"admin"`         | 管理员用户名                                                                                                    |
| `config.password`    | string   | 必填                | 管理员密码                                                                                                     |
| `config.installType` | string   | `"SNSD"`          | 部署模式：`SNSD`（单节点单盘）/ `SNMD`（单节点多磁盘）/ `MNMD`（多节点多磁盘）                                                        |
| `config.volumes`     | string   | `"/data/rustfs0"` | 存储路径。SNSD: `/data/rustfs0`；SNMD: `/data/rustfs{0...3}`；MNMD: `http://node{1...4}:9040/data/rustfs{0...3}` |
| `config.obsEndpoint` | string   | `""`              | RustFS 指标上报的 OTLP/HTTP 地址；启用 `baseOtelCollector` 时由 plan 自动按 collector 节点覆盖                  |

**相关 DAG 步骤**：`init-rustfs`（步骤 4，条件：`rustfs.enable`）

---

### baseOtelCollector

控制引导期 MySQL、Nexus、RustFS 指标的独立 OTel Collector。默认关闭；启用后 Collector 将指标暂存到 RustFS S3，不依赖尚未安装的 Doris。

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enable` | bool | `false` | 是否安装引导期基础 Collector |
| `node` | string | 必填 | Collector 运行节点 hostname |
| `otlpHttpPort` | string | `"5318"` | RustFS OTLP/HTTP 上报端口（刻意避开纳管 OTELCOLLECTOR 硬编码的 4318，两者可能同节点共存） |
| `otlpGrpcPort` | string | `"5317"` | OTLP/gRPC 接收端口（同上，避开纳管 OTELCOLLECTOR 的 4317） |
| `selfMetricsPort` | string | `"8899"` | Collector 自监控指标端口 |
| `s3Bucket` | string | `"otel"` | RustFS S3 bucket；Collector 安装前会使用配置凭据幂等创建或确认可访问 |
| `s3Prefix` | string | `"otel-base"` | S3 对象前缀 |
| `s3Region` | string | `"us-east-1"` | awss3 exporter 必填 region |
| `memLimitMiB` | int | `512` | Collector 内存限制（MiB） |
| `mysqldExporter.enable` | bool | `true` | 是否在 MySQL 节点安装 mysqld_exporter |
| `mysqldExporter.port` | string | `"9104"` | exporter 监听端口 |
| `mysqldExporter.monitorUser` | string | `"exporter"` | MySQL 最小权限监控账号 |
| `mysqldExporter.monitorPassword` | string | 必填 | 监控账号密码，`create config` 自动随机生成 |
| `nexusMetrics.metricsUser` | string | `"metrics"` | Nexus `nx-metrics-all` 账号 |
| `nexusMetrics.metricsPassword` | string | 必填 | Nexus metrics 账号密码，`create config` 自动随机生成 |
| `nexusMetrics.metricsPath` | string | `"/service/rest/metrics/prometheus"` | Nexus Prometheus 指标路径 |

**相关 DAG 步骤**：`init-base-otel-collector`（紧随 RustFS）、`init-registry`（创建 metrics 账号）、`init-mysqld-exporter`（紧随 MySQL 应用账号初始化）

启用时，plan 生成阶段会统一补齐上述默认值，并在任何 SSH/REST 操作前校验节点引用、端口范围、bucket 名、双架构制品名及 RustFS/MySQL/Nexus 必需凭据。Collector 和 exporter 使用稳定安装入口加 `releases/<version>` 目录；配置或版本未变化时保持进程运行。

---

### yumServer

本地 Yum/Apt 离线源服务器（HTTP 文件服务器）。

|      字段      |   类型   |   默认    |        说明         |
|--------------|--------|---------|-------------------|
| `enable`     | bool   | `false` | 是否启用离线源服务器        |
| `node`       | string | 必填      | 运行离线源的节点 hostname |
| `listenPort` | int    | `4080`  | HTTP 监听端口         |

**相关 DAG 步骤**：`init-offline-server`（步骤 14）、`init-offline-nodes`（步骤 15，条件：`yumServer.enable || registry.enable`）

---

### nmapServer

Nmap 网络扫描工具安装。

|    字段    |   类型   |   默认    |        说明         |
|----------|--------|---------|-------------------|
| `enable` | bool   | `false` | 是否安装 nmap/netstat |
| `node`   | string | 必填      | 安装目标节点 hostname   |

**相关 DAG 步骤**：`init-nmap`（步骤 21，条件：`nmapServer.enable`）

---

### mysql

MySQL 8.x 安装与数据库初始化。

|          字段          |   类型   |    默认    |                  说明                   |
|----------------------|--------|----------|---------------------------------------|
| `enable`             | bool   | `false`  | 是否安装 MySQL                            |
| `force`              | bool   | `false`  | 若 MySQL 已存在则强制重装                      |
| `user`               | string | `"root"` | MySQL root 用户名                        |
| `password`           | string | 必填       | MySQL root 密码（`create config` 自动随机生成） |
| `port`               | int    | `3306`   | MySQL 端口                              |
| `node`               | string | 必填       | 安装 MySQL 的节点 hostname                 |
| `appDbs[*].account`  | string | 必填       | 应用数据库用户名                              |
| `appDbs[*].password` | string | 必填       | 应用数据库密码                               |
| `appDbs[*].dbName`   | string | 必填       | 数据库名                                  |

**典型 appDbs 配置**（datasophon、hive、dolphinscheduler、ustream、nacos、bigdata、juicefs 等）。

**相关 DAG 步骤**：`init-mysql`（步骤 24）、`init-mysql-app-db`（步骤 25，每个 appDb 一个 Action）

---

### ntpServer

NTP 时钟服务。

|    字段    |   类型   |   默认    |               说明                |
|----------|--------|---------|---------------------------------|
| `enable` | bool   | `false` | 是否配置 NTP                        |
| `node`   | string | 必填      | NTP 服务端节点 hostname（其余节点自动配为客户端） |

**相关 DAG 步骤**：`init-ntp-server`（步骤 22）、`init-ntp-slave`（步骤 23，排除 server 自身）

---

### kubernetes

Kubernetes 集群部署（使用 Sealos 方案）。

#### kubernetes（顶层）

|    字段    |  类型  |   默认    |                 说明                 |
|----------|------|---------|------------------------------------|
| `enable` | bool | `false` | 是否部署 K8s。`false` 时所有 `k8s-*` 步骤均跳过 |
| `force`  | bool | `false` | 强制重装 K8s                           |

> `onlyInstall` 字段已移除，由顶层 `type: kubernetes` 取代。

#### kubernetes.baseServices

|      字段       |    类型    |   默认   |            说明             |
|---------------|----------|--------|---------------------------|
| `namespaces`  | []string | `[]`   | 预创建的命名空间列表                |
| `masters`     | []string | 必填     | K8s Master 节点 hostname 列表 |
| `nodes`       | []string | 必填     | K8s Worker 节点 hostname 列表 |
| `sealos`      | bool     | `true` | 是否使用 Sealos 安装 K8s        |
| `kubernetesI` | bool     | `true` | 是否安装 Kubernetes 组件        |
| `helmI`       | bool     | `true` | 是否安装 Helm（集群内）            |
| `calicoI`     | bool     | `true` | 是否安装 Calico CNI           |
| `ingressI`    | bool     | `true` | 是否安装 Ingress NGINX        |

**相关 DAG 步骤**：`k8s-base-services`（步骤 26）

#### kubernetes.kuboardI

|     字段      |    类型    |   默认    |           说明            |
|-------------|----------|---------|-------------------------|
| `enable`    | bool     | `false` | 是否安装 Kuboard            |
| `node`      | string   | 必填      | 安装 Kuboard 的节点 hostname |
| `etcdNodes` | []string | 必填      | etcd 节点列表（至少 3 个）       |

**相关 DAG 步骤**：`k8s-kuboard`（步骤 27，条件：`kubernetes.enable && kuboardI.enable`）

#### kubernetes.k8sTools

|    字段     |  类型  |   默认   |                   说明                   |
|-----------|------|--------|----------------------------------------|
| `docker`  | bool | `true` | 是否在 K8s 节点安装 Docker                    |
| `helm`    | bool | `true` | 是否安装 Helm CLI 工具                       |
| `helmify` | bool | `true` | 是否安装 Helmify（K8s 清单 → Helm Chart 转换工具） |
| `kubectl` | bool | `true` | 是否安装 kubectl                           |

---

### packages

各组件安装包的文件名，与 `<productPackagesPath>/base/` 目录拼接定位实际文件。目标为远端节点且未挂载共享目录时，CLI 会按需通过 SFTP 分发所需的 Collector/exporter 制品。`x86_64` 和 `aarch64` 分别对应两种架构。

|          字段           |   类型   |                     示例值                     |
|-----------------------|--------|---------------------------------------------|
| `os`                  | string | `openEuler-22.03-LTS-SP3.tar.gz`            |
| `config`              | string | `config.tar.gz`                             |
| `soft`                | string | `packages.tar.gz`                           |
| `nexus.x86_64`        | string | `nexus-3.85.0-03-linux-x86_64.tar.gz`       |
| `nexus.aarch64`       | string | `nexus-3.85.0-03-linux-aarch_64.tar.gz`     |
| `mysql.x86_64`        | string | `mysql-8.0.28-1.el8.x86_64.rpm-bundle.tar`  |
| `mysql.aarch64`       | string | `mysql-8.0.28-1.el8.aarch64.rpm-bundle.tar` |
| `rustfs.x86_64`       | string | `rustfs-linux-x86_64-musl-1.0.0.tar.gz`     |
| `rustfs.aarch64`      | string | `rustfs-linux-aarch64-musl-1.0.0.tar.gz`    |
| `otelColContrib.x86_64` | string | `otelcol-contrib_0.156.0_linux_amd64.tar.gz` |
| `otelColContrib.aarch64` | string | `otelcol-contrib_0.156.0_linux_arm64.tar.gz` |
| `mysqldExporter.x86_64` | string | `mysqld_exporter-0.16.0.linux-amd64.tar.gz` |
| `mysqldExporter.aarch64` | string | `mysqld_exporter-0.16.0.linux-arm64.tar.gz` |
| `sealos.x86_64`       | string | `sealos_5.1.0_linux_amd64.tar.gz`           |
| `sealos.aarch64`      | string | `sealos_5.1.0_linux_arm64.tar.gz`           |
| `kubernetesI.x86_64`  | string | `kubernetes-v1.31.8-x86.tar`                |
| `kubernetesI.aarch64` | string | `kubernetes-v1.31.8-arm.tar`                |
| `helmI.x86_64`        | string | `helm-v4.0.1-x86.tar`                       |
| `helmI.aarch64`       | string | `helm-v4.0.1-arm.tar`                       |
| `calicoI.x86_64`      | string | `calico-v3.28.1-x86.tar`                    |
| `calicoI.aarch64`     | string | `calico-v3.28.1-arm.tar`                    |
| `ingressI.x86_64`     | string | `ingress-nginx-4.1.0-x86.tar`               |
| `ingressI.aarch64`    | string | `ingress-nginx-4.1.0-arm.tar`               |
| `kuboardI.x86_64`     | string | `kuboard-v3-x86.tar`                        |
| `kuboardI.aarch64`    | string | `kuboard-v3-arm.tar`                        |
| `helmify.x86_64`      | string | `helmify_Linux_x86_64.tar.gz`               |
| `helmify.aarch64`     | string | `helmify_Linux_arm64.tar.gz`                |
| `docker.x86_64`       | string | `docker-x86-29.3.1.tgz`                     |
| `docker.aarch64`      | string | `docker-arm-29.3.1.tgz`                     |
| `helm.x86_64`         | string | `helm-v4.0.1-linux-amd64.tar.gz`            |
| `helm.aarch64`        | string | `helm-v4.0.1-linux-arm64.tar.gz`            |
| `kubectl.x86_64`      | string | `kubectl-x86`                               |
| `kubectl.aarch64`     | string | `kubectl-arm`                               |

---

## nodes

集群节点列表。`create cluster` 执行时，工具会用当前机器的本机 IP 匹配 `nodes[*].ip`，找到的节点作为"本地节点"直接用 LocalExecutor 操作，其余节点通过 SSH 远程操作。

**nodes 至少需要 1 个节点。**

|     字段     |   类型   | 必填 |                    说明                     |
|------------|--------|----|-------------------------------------------|
| `ip`       | string | 是  | 节点 IP                                     |
| `port`     | int    | 是  | SSH 端口（通常为 22）                            |
| `user`     | string | 是  | SSH 登录用户名（通常为 root）                       |
| `password` | string | 否  | SSH 密码（`sshAuthType: PUBLICKEY` 时可留空）     |
| `hostname` | string | 是  | 节点 hostname（须与 OS 配置一致，其他字段的 `node` 引用该值） |

```yaml
nodes:
  - ip: 192.168.1.10
    port: 22
    user: root
    password: "YourPassword"
    hostname: app1
  - ip: 192.168.1.11
    port: 22
    user: root
    password: "YourPassword"
    hostname: app2
```

---

## 节点扩容

新节点不再通过配置文件提前声明。请直接对单节点执行：

```bash
datasophon-cli create node \
  -p /data/datasophon --installPath /opt/install -n /data/datasophon/datasophon-init/packages \
  --ip 192.168.1.20 --user root --password "YourPassword" --port 22 --hostname app7
```

命令完成后，该节点会**自动追加**到 `cluster-sample.yml` 的 `nodes` 列表。详见 [`create node`](./commands/create/node.md)。

---

## 完整配置示例

完整带注释的参考示例见 `internal/config/configs/cluster-config.yml`（`create config` 的输出模板）。
