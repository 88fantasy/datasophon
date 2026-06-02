# Datasophon 离线 K8s 部署指南

> 拓扑规格：1 master + 1 worker + 1 middleware（三节点最小可运行集群）
> 基于分支 `refactor/cluster-type`，配置文件真相之源：`datasophon-cli-go/internal/config/configs/cluster-config.yml`

---

## 注意事项

> 以下事项是在实际部署中容易犯错的地方，请在操作前仔细阅读。

1. **mw1 绝对不能加入 K8s 集群**
   `cluster-config.yml::kubernetes.baseServices.nodes` 中只写 `["w1"]`，不能包含 `mw1`。
   若 mw1 被误加入 K8s 再移除，etcd 会留下残留 member，需要手动 `etcdctl member remove` 修复，风险极高。

2. **datasophon-api HTTP 端口为 8080，Nexus 使用默认的 8081，两者不冲突**
   datasophon-api 的 Spring Boot 默认端口即 8080，Nexus 保持默认端口 8081，部署在同一节点 mw1 上不会互相影响。
   Nexus Docker Registry 仍使用 8083。

3. **datasophon-worker 必须以双形态分别部署**
   - mw1（非 K8s 节点）：systemd 裸机安装，管理本节点 OS 级服务（MySQL/Nexus/Rustfs/NTP 的启停）
   - K8s 集群：通过 Deployment 安装，`hostNetwork: true`，管理 K8s 资源（Deployment/Service/ConfigMap 操作）
   两种 worker 都向 `mw1:18081` 注册，缺一不可。

4. **containerd 在 m1 和 w1 上都要安装**
   master 节点也是 K8s 节点，会调度 system Pod（coredns/kube-proxy/calico），必须有 containerd 运行时。

5. **所有离线镜像须先上传到 Nexus:8083**
   m1/w1 的 containerd 通过 `hosts.toml` mirror 机制拉取镜像，仅指向 `mw1:8083`，不访问公网。
   部署前必须完成 `datasophon-cli init registry-upload` 将镜像推入 Nexus。

6. **Pod worker 的 `MASTER_HOST` 必须是 mw1 的实际 IP**
   Pod 运行在 K8s 集群内，不能用 `127.0.0.1`。必须填写 mw1 可达的宿主机 IP（非 K8s Service IP）。

7. **cluster-config.yml 改动后需重新执行 `create cluster`**
   CLI 在启动时一次性 hash 整个 config，incremental 执行不会自动检测配置变更。

8. **NTP 先于所有 K8s 组件部署**
   sealos 安装 etcd 时校验节点时间差 < 2s，若 NTP 未同步会导致 etcd 集群建立失败。

---

## 一、节点角色与组件清单

| 节点 | 角色 | 服务端组件 | 客户端工具 | 不安装 |
|---|---|---|---|---|
| **m1** | K8s control-plane（唯一 master） | kube-apiserver / etcd / kubelet / kube-proxy / containerd / Calico / **Kuboard**（NodePort 30080） | **helm** / kubectl / helmify | Ingress-nginx；裸机 datasophon-worker |
| **w1** | K8s worker | kubelet / kube-proxy / containerd / Calico / **Ingress-nginx** / **datasophon-worker Pod**（hostNetwork，18082） | — | etcd / apiserver；裸机 datasophon-worker |
| **mw1** | 制品库 + DB + 管理端（不进 K8s） | **datasophon-api**（8080 HTTP + 18081 gRPC）/ **datasophon-worker 裸机**（18082）/ **Nexus**（8081 + 8083，含 yum 仓库）/ **MySQL**（3306）/ **NTP**（123/udp）/ **Rustfs**（9040 + 9041）/ Docker（unix socket） | nmap | K8s 任何组件；独立 YumServer/httpd |

> **mw1 组件安装方式**：Nexus / MySQL / Rustfs 由 `datasophon-cli create cluster -a initALL` 通过 SSH 远程安装（联网下载安装包 → 拷贝到 mw1 → CLI 执行安装步骤）。datasophon-worker 以 systemd 方式手动部署（见六、B）。datasophon-api 从 `datasophon-assembly` tar.gz 解压启动，不经过 CLI 管控。

---

## 二、端口速查表

### mw1（middleware 节点）

| 服务 | 端口/协议 | 说明 |
|---|---|---|
| datasophon-api HTTP | **8080**/TCP | 管理 UI 入口（路径 `/ddh`） |
| datasophon-api gRPC | **18081**/TCP | Worker 注册 / 心跳 / 反向回调入口 |
| datasophon-worker gRPC（裸机） | **18082**/TCP | API 向 mw1 本身下发 OS 级命令 |
| Nexus Web（yum/raw/helm） | **8081**/TCP | 制品库 UI；yum 源路径 `/repository/yum/<arch>/<os>/`；m1/w1 离线包均从此拉取 |
| Nexus Docker Registry | **8083**/TCP | containerd mirror（docker.io / registry.k8s.io / quay.io / gcr.io） |
| MySQL | **3306**/TCP | datasophon-api 业务 DB |
| Rustfs API（S3） | **9040**/TCP | S3 兼容对象存储 API |
| Rustfs Web UI | **9041**/TCP | 浏览器控制台 |
| NTP（chrony） | **123**/UDP | 集群所有节点 NTP 对时源 |
| Docker daemon | unix socket | `/run/docker.sock`，不监听 TCP |
| SSH | **22**/TCP | CLI 远程执行入口 |

### m1（K8s master 节点）

| 服务 | 端口/协议 | 说明 |
|---|---|---|
| kube-apiserver | **6443**/TCP | K8s API 入口（kubectl/worker/Kuboard） |
| etcd client | **2379**/TCP | apiserver 访问 etcd（仅内部） |
| etcd peer | **2380**/TCP | etcd 节点间同步（单 master 时本机） |
| kubelet | **10250**/TCP | apiserver → kubelet 管理通道 |
| kube-proxy healthz | **10256**/TCP | 本机健康检查 |
| Calico BGP | **179**/TCP | 节点间 overlay 网络（默认 IPIP） |
| Kuboard | **30080**/TCP（NodePort） | K8s 管理 UI |
| containerd | unix socket | `/run/containerd/containerd.sock` |
| SSH | **22**/TCP | CLI 远程执行入口 |

### w1（K8s worker 节点）

| 服务 | 端口/协议 | 说明 |
|---|---|---|
| kubelet | **10250**/TCP | apiserver → kubelet 管理通道 |
| kube-proxy healthz | **10256**/TCP | 本机健康检查 |
| Calico BGP | **179**/TCP | 节点间 overlay 网络 |
| Ingress-nginx | **80/443**/TCP | 外部用户业务流量入口 |
| datasophon-worker Pod | **18082**/TCP | `hostNetwork: true`，API 向 K8s 集群下发命令 |
| NodePort 范围 | **30000-32767**/TCP | K8s 服务 NodePort 备用 |
| containerd | unix socket | `/run/containerd/containerd.sock` |
| SSH | **22**/TCP | CLI 远程执行入口 |

---

## 三、关键网络连通性

| 方向 | 端口 | 用途 |
|---|---|---|
| m1, w1 → mw1:8083 | TCP | containerd 离线镜像 mirror（docker.io / registry.k8s.io / quay.io / gcr.io） |
| m1, w1 → mw1:8081 | TCP | Nexus 所有仓库（yum 离线源 + raw 安装包 + helm）；YumServer/httpd 已由 Nexus yum 仓库替代 |
| m1, w1 → mw1:123 | UDP | chrony NTP 对时 |
| w1 → m1:6443 | TCP | kubelet/kube-proxy → apiserver |
| m1 → w1:10250 | TCP | apiserver / metrics-server → kubelet |
| 外部 → m1:30080 | TCP | Kuboard 控制台 |
| 外部 → w1:80/443 | TCP | Ingress-nginx 业务流量 |
| 外部 → mw1:8080 | TCP | datasophon-api 管理 UI |
| mw1 worker（裸机）→ 127.0.0.1:18081 | TCP | 注册 + 30s 心跳 |
| K8s Pod worker（w1）→ mw1:18081 | TCP | 注册 + 30s 心跳（出 K8s 集群，需 mw1 IP 可达） |
| datasophon-api（mw1）→ 127.0.0.1:18082 | TCP | 下发命令给 mw1 裸机 worker |
| datasophon-api（mw1）→ w1:18082 | TCP | 下发命令给 Pod worker（依赖 hostNetwork） |

---

## 四、ASCII 拓扑图

```
   ┌───────────────────────── External Network ─────────────────────────┐
   │                                                                    │
   │  Browser  Admin/User  kubectl(client)                              │
   │                                                                    │
   └───────┬──────────────┬──────────────┬──────────────┬───────────────┘
           │              │              │              │
        8080/ddh        30080           6443          80/443
        (DS-API UI)    (Kuboard)      (K8s API)      (Ingress)
        9041                                          + NodePort
        (rustfs UI)                                    30000-32767
           │              │              │              │
           │              ▼              ▼              ▼
           │       ┌──────────────────────────┐  ┌────────────────────────────┐
           │       │ m1   K8s control-plane    │  │ w1   K8s worker            │
           │       │ ─────────────────────────│  │ ───────────────────────────│
           │       │ kube-apiserver  :6443    │◀▶│ kubelet            :10250  │
           │       │ etcd     :2379  / :2380  │  │ kube-proxy         :10256  │
           │       │ kubelet         :10250   │  │ containerd         (uds)   │
           │       │ kube-proxy      :10256   │  │ Calico  BGP        :179    │
           │       │ containerd      (uds)    │◀▶│ Ingress-nginx      :80/443 │
           │       │ Calico  BGP     :179     │  │ NodePort      :30000-32767 │
           │       │ Kuboard NodePort:30080   │  │ SSH                :22     │
           │       │ Helm / Helmify / kubectl │  │ ┌────────────────────────┐ │
           │       │ SSH             :22      │  │ │ Pod: datasophon-worker │ │
           │       │                          │  │ │  Deployment replicas=1 │ │
           │       │                          │  │ │  hostNetwork: true     │ │
           │       │                          │  │ │  port :18082 gRPC      │ │
           │       │                          │  │ └────────┬───────────────┘ │
           │       └─────────┬────────────────┘  └──────────┬─────────────────┘
           │                 │                              │
           │     pull image  │ containerd                   │ containerd
           │     8083 mirror │ → mw1:8083                   │ 8083 mirror
           │  ┌──────────────┘                              │
           │  │      ┌──────────────────────────────────────┘
           │  │      │      Pod worker → mw1:18081 (注册/心跳)
           │  │      │      mw1 api    → w1:18082    (下发 K8s 资源命令)
           │  ▼      ▼      ▼
           │  ┌─────────────────────────────────────────────────────────────┐
           │  │ mw1   Middleware（不在 K8s 集群内，CLI 操作入口）             │
           │  │ ───────────────────────────────────────────────────────────│
           │  │ ┌─────────────────────────┐  ┌───────────────────────────┐ │
           │  │ │ datasophon-api          │  │ datasophon-worker（裸机）  │ │
           │  │ │   :8080 HTTP (/ddh)     │◀▶│   :18082 gRPC             │ │
           │  │ │   :18081 gRPC server    │  │   注册 → 127.0.0.1:18081  │ │
           │  │ │   (接受 worker 注册)    │  │   管理本节点（systemctl）  │ │
           │  │ └────────┬────────────────┘  └───────────────────────────┘ │
           │  │          │ JDBC 127.0.0.1                                  │
           │  │          ▼                                                 │
           └──┤ Nexus Web        :8081  (yum/raw/helm；含 yum 离线源仓库)    │
              │ Nexus Docker     :8083  ◀── mirror 给 m1 + w1              │
              │ MySQL            :3306  ◀── localhost from API             │
              │ NTP (chronyd)    :123/udp ◀── 全集群对时                    │
              │ Rustfs API (S3)  :9040                                     │
              │ Rustfs Web UI    :9041                                     │
              │ Docker daemon    (uds /run/docker.sock)                    │
              │ SSH              :22    ◀── CLI 远程执行入口                │
              └────────────────────────────────────────────────────────────┘

  worker 注册 / 命令下发链路:
    [mw1 裸机 worker]  →  127.0.0.1:18081   注册 + 30s 心跳
    [w1 Pod worker]    →  mw1:18081          注册 + 30s 心跳（出 K8s 集群）
    [mw1 api]          →  127.0.0.1:18082   下发命令（mw1 worker）
    [mw1 api]          →  w1:18082           下发命令（Pod worker，via hostNetwork）

  基础设施链路:
    m1, w1   →  mw1:8083   离线镜像 mirror（containerd hosts.toml）
    m1, w1   →  mw1:8081   Nexus 仓库（yum 离线源 /repository/yum/<arch>/<os>/ + raw + helm）
    m1, w1   →  mw1:123    chrony NTP 对时
    w1       →  m1:6443    kubelet → apiserver
    m1       →  w1:10250   apiserver → kubelet

  WorkerRegistry 视角（datasophon-api 内存表）:
    hostname=mw1  →  WorkerEndpoint{ip:127.0.0.1, port:18082}
    hostname=w1   →  WorkerEndpoint{ip:<w1-ip>,   port:18082}
```

---

## 五、cluster-config.yml 关键配置

基于 `datasophon-cli-go/internal/config/configs/cluster-config.yml` 的 sample，按此三节点拓扑做以下调整：

```yaml
kubernetes:
  enable: true
  baseServices:
    namespaces: ["prod"]
    masters: ["m1"]          # 单 master
    nodes:   ["w1"]          # 仅 w1 进 K8s，mw1 不在此列表
    sealos: true
    kubernetesI: true
    helmI: true
    calicoI: true
    ingressI: true
  kuboardI:
    enable: true
    node: "m1"
    etcdNodes: ["m1"]        # 单 master 场景

registry:
  enable: true
  node: "mw1"
  config:
    webPort: 8081            # Nexus 默认端口；datasophon-api 使用 8080，两者不冲突
    dockerHttpPort: 8083
    repositories: ["yum", "raw", "apt", "docker", "helm"]  # CLI 自动在 Nexus 创建

yumServer:
  enable: false              # Nexus yum 仓库已接管离线源，不再安装 httpd/apache
  node: "mw1"

mysql:
  node: "mw1"
  port: 3306

ntpServer:
  node: "mw1"

nmapServer:
  node: "mw1"

rustfs:
  enable: true
  nodes: ["mw1"]
  config:
    apiPort: 9040
    webPort: 9041
```

> datasophon-api 不受 CLI 管控，需从 `datasophon-assembly` 产出的 tar.gz 在 mw1 上手动解压启动。

### mw1 组件安装工作流

Nexus / MySQL / Rustfs 由 `datasophon-cli` 通过 SSH 远程安装，无需提前在 mw1 上操作：

```
┌─────────────────────────────────────────────────────────────────┐
│  operator（可在 m1 或本机）                                       │
│                                                                 │
│  1. 联网下载安装包（nexus tar.gz / mysql rpm-bundle / rustfs gz）│
│     → 存放至 $DATASOPHON_INIT/packages/                         │
│  2. 编辑 cluster-config.yml（mw1 IP、端口、密码）               │
│  3. datasophon-cli create cluster -a initALL \                 │
│       --config cluster-config.yml                               │
│     CLI 自动通过 SSH 将包 sftp 到 mw1 并远程执行安装脚本        │
└─────────────────────────────────────────────────────────────────┘
```

CLI 执行顺序（与 mw1 相关的关键步骤）：
1. `init-registry`：安装 Nexus，保持默认端口 8081，启动后通过 REST API 创建 `yum / raw / apt / docker / helm` 仓库
2. `init-registry-upload`：将所有安装包上传到 Nexus raw 仓库
3. `init-offline-nodes`：在 m1/w1 配置 yum baseurl → `http://mw1:8081/repository/yum/<arch>/<os>/`（无需 httpd）
4. `init-mysql`：安装 MySQL 8，初始化 datasophon / hive 等数据库
5. `init-rustfs`：安装 Rustfs，启动 S3 服务（9040 API + 9041 Web）

**datasophon-worker（mw1 裸机形态）** 单独手工部署，参见六、A 的 systemd 模板。

---

## 六、datasophon-worker 部署模板

### A. mw1 裸机部署（systemd）

```ini
# /etc/systemd/system/datasophon-worker.service
[Unit]
Description=Datasophon Worker (bare-metal, manages mw1 itself)
After=network.target datasophon-api.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/datasophon/worker
ExecStart=/usr/bin/java -jar /opt/datasophon/worker/datasophon-worker.jar \
  --grpc.server.port=18082 \
  --master.host=127.0.0.1 \
  --master.grpc.port=18081
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### B. K8s 集群 Deployment（hostNetwork: true）

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: datasophon-worker
  namespace: prod
spec:
  replicas: 1
  selector:
    matchLabels: { app: datasophon-worker }
  template:
    metadata:
      labels: { app: datasophon-worker }
    spec:
      hostNetwork: true
      dnsPolicy: ClusterFirstWithHostNet
      nodeSelector:
        node-role.kubernetes.io/worker: ""   # 调度到 w1
      containers:
      - name: worker
        image: <mw1-ip>:8083/datasophon/datasophon-worker:3.0-SNAPSHOT
        ports:
        - containerPort: 18082
          hostPort: 18082
        env:
        - name: MASTER_HOST
          value: "<mw1-ip>"                  # 必须是 mw1 实际 IP，不能用 127.0.0.1
        - name: MASTER_GRPC_PORT
          value: "18081"
        - name: WORKER_GRPC_PORT
          value: "18082"
        - name: KUBERNETES_MODE
          value: "true"
        readinessProbe:
          tcpSocket: { port: 18082 }
          periodSeconds: 10
```

---

## 七、部署后验证清单

| # | 验证项 | 命令 | 期望结果 |
|---|---|---|---|
| 1 | 节点角色 | `kubectl get nodes -o wide` | 列出 m1（control-plane）+ w1（worker）；**mw1 不在列表** |
| 2 | mw1 端口 | `ss -lntup \| grep -E '8080\|8081\|8083\|3306\|9040\|9041\|18081\|18082'` | 全部 LISTEN |
| 3 | mw1 NTP | `ss -lnup \| grep ':123 '` | chronyd LISTEN |
| 4 | m1 端口 | `ss -lntup \| grep -E '6443\|2379\|2380\|10250\|30080'` | 全部 LISTEN |
| 5 | w1 端口 | `ss -lntup \| grep -E '10250\|80\|443'` | 全部 LISTEN |
| 6 | containerd mirror | 在 m1/w1：`cat /etc/containerd/certs.d/docker.io/hosts.toml` | 含 `http://<mw1>:8083` |
| 7 | 镜像拉取链路 | 在 w1：`crictl pull docker.io/library/busybox:latest` | 从 mw1:8083 拉取，不走公网 |
| 8 | datasophon-api 健康 | `curl http://mw1:8080/ddh/actuator/health` | `{"status":"UP"}` |
| 9 | MySQL 联通 | `mysql -h mw1 -P 3306 -u datasophon -p` | 登录成功，列出 `datasophon` 库 |
| 10 | Kuboard 访问 | 浏览器打开 `http://m1:30080` | 出现登录页 |
| 11 | 离线 yum 源 | 在 m1/w1：`yum repolist` | 仅列出指向 `mw1:8081` 的仓库 |
| 12 | NTP 对时 | 在 m1/w1：`chronyc sources` | reference 指向 mw1 |
| 13 | Rustfs S3 | `curl http://mw1:9040/` | 返回 S3 XML 错误页（API 已起） |
| 14 | Ingress 反代 | 部署 demo Ingress，`curl http://w1/` | 200/301 |
| 15 | mw1 裸机 worker 端口 | `ss -lntp \| grep ':18082 '` | datasophon-worker LISTEN |
| 16 | mw1 worker 注册 | `grep "WorkerRegistry" <api-log>` | 出现 hostname=mw1 注册成功 |
| 17 | Pod worker 调度 | `kubectl get pod -n prod -l app=datasophon-worker -o wide` | Status=Running，落点=w1 |
| 18 | Pod worker hostNetwork | 在 w1：`ss -lntp \| grep ':18082 '` | Pod 进程占用 18082 |
| 19 | Pod worker 注册 | `grep "hostname=w1" <api-log>` | 出现 w1 注册条目 |
| 20 | 端到端命令下发 | DataSophon UI 点击 mw1 和 w1 的 Ping 测试 | 两台均返回 OK |

---

## 八、本地模拟测试（Docker Compose）

> 文件位置：`deploy/compose/kubernetes/docker-compose.yml`
> 无需真实 K8s，用独立容器模拟 mw1/w1 的 datasophon-worker 双形态。

### 服务清单

| 容器 | 模拟对象 | 宿主机端口 |
|---|---|---|
| `ddh-k8s-mysql` | mw1 MySQL | 3308 |
| `ddh-k8s-nexus` | mw1 Nexus（制品库） | **8081** |
| `ddh-k8s-nexus-init` | Nexus 初始化（创建 yum/raw/docker/helm 仓库，一次性） | — |
| `ddh-k8s-minio` | mw1 Rustfs（MinIO S3 兼容替代） | 9040 / 9041 |
| `ddh-k8s-api` | mw1 datasophon-api | **8080** / 18081 |
| `ddh-k8s-seed` | 集群记录初始化（一次性） | — |
| `ddh-k8s-mw-worker` | mw1 裸机 worker（hostname=mw1） | 18082 |
| `ddh-k8s-k8s-worker` | K8s Pod worker（hostname=w1） | 18083→18082 |

### 前置准备

```bash
export JH17=~/Library/Java/JavaVirtualMachines/jbr-17.0.12-1/Contents/Home
JAVA_HOME=$JH17 ./mvnw clean package -DskipTests -s ~/.m2/setting.xml

# 确认产物
ls datasophon-api/target/datasophon-manager-3.0-SNAPSHOT.tar.gz
ls datasophon-worker/target/datasophon-worker.tar.gz
```

### 启动

```bash
cd deploy/compose/kubernetes
docker compose up --build
```

> ⚠️ Nexus 首次冷启动约 1-2 分钟，compose 会等待健康检查通过后再启动后续服务。

### 验证

```bash
# API 健康
curl http://localhost:8080/ddh/actuator/health

# mw1 裸机 worker 注册
docker compose logs mw-worker | grep -iE "registered"

# K8s Pod worker 注册
docker compose logs k8s-worker | grep -iE "registered"

# WorkerRegistry 记录（应有 mw1 和 w1 两条）
docker compose logs mw-api | grep -iE "WorkerRegistry"

# Nexus Web UI（admin / admin123）
open http://localhost:8081

# MinIO 控制台（模拟 Rustfs，minioadmin / minioadmin123）
open http://localhost:9041
```

### 与真实部署的差异说明

| 项目 | 真实部署 | Compose 模拟 |
|---|---|---|
| datasophon-api 端口 | 8080（Spring Boot 默认） | 同，host 侧映射 8080 |
| Nexus 端口 | 8081（默认端口，无需重配置） | 同，host 侧映射 8081 |
| Rustfs | 自研 S3 存储 | MinIO（S3 兼容） |
| YumServer | 已由 Nexus yum 仓库替代，无独立服务 | 无（Nexus-init 创建 yum hosted repo） |
| K8s worker hostNetwork | hostNetwork:true，占用 w1:18082 | 独立容器，hostname=w1，host 侧映射 18083 |
| NTP | chronyd（:123/udp，CAP_SYS_TIME） | 未模拟（容器内时钟依赖宿主机） |
| m1 K8s 控制面 | kube-apiserver/etcd/kubelet | 未模拟 |

### 清理

```bash
docker compose -f docker-compose.topology.yml down      # 停止，保留 volume
docker compose -f docker-compose.topology.yml down -v   # 完全重置
```

---

## 九、关键文件参考

| 用途 | 文件 |
|---|---|
| 端口配置真相之源 | `datasophon-cli-go/internal/config/configs/cluster-config.yml` |
| containerd 离线 mirror 逻辑 | `datasophon-cli-go/internal/cli/init/containerd.go:144-204` |
| Kuboard NodePort 30080 | `datasophon-cli-go/internal/cli/init/kuboard.go:95` |
| Docker daemon 仅 unix socket | `datasophon-cli-go/internal/cli/init/docker.go:99-158` |
| 组件 → 节点映射逻辑 | `datasophon-cli-go/internal/plan/builders_cluster.go` |
| K8s baseServices 注册顺序 | `datasophon-cli-go/internal/plan/registry.go` |
| gRPC 端口常量 | `datasophon-grpc-api/.../GrpcConstants.java` |
| Nexus yum 仓库创建逻辑 | `datasophon-cli-go/internal/plan/registry_task.go::yumRepoCreate` |
| 节点 yum 源配置逻辑（Nexus 路径） | `datasophon-cli-go/internal/cli/init/offline_slave.go:43-73` |

---

## 十、组件版本参考

> 查询时间：2026-05-31。"当前配置版本"来自 `cluster-config.yml` 的 `packages` 字段；"最新稳定版"来自各官方仓库 Release 页。

### mw1 中间件组件

| 组件 | 当前配置版本 | 最新稳定版 | 状态 |
|---|---|---|---|
| **MySQL** | 8.0.28 | **8.4.9 LTS** | ⚠️ 8.0.x 已于 2026-04 EOL，**强烈建议升级到 8.4 LTS** |
| **Nexus Repository 3** | 3.85.0 | **3.92.3** | 可升级，Nexus 2 已于 2025-06-30 停服 |
| **Rustfs** | 1.0.0 | 1.0.0-beta.6 | ℹ️ 仍在 Beta，GA 尚未正式发布；生产谨慎评估 |

### K8s 集群核心组件

| 组件 | 当前配置版本 | 最新稳定版 | 状态 |
|---|---|---|---|
| **Kubernetes** | 1.31.8 | **1.36.1** | 1.31 仍在受支持窗口（N-2 策略），可按需升级 |
| **sealos**（K8s 安装器） | 5.1.0 | **5.1.1** | 小版本更新，可升级 |
| **containerd** | 2.3.0 | **2.3.0** ✓ | 2026-04 发布的首个 LTS 版本，已是最新 |
| **runc** | 未固定版本 | **1.4.0** | 建议在 packages 中固定版本 |
| **CNI plugins** | 1.6.0 | **1.9.1** | 2026-03-16 发布，含安全更新，建议升级 |
| **Calico** | 3.28.1 | **3.31.2** | 可升级，3.31 开始 eBPF dataplane 正式 GA |

### K8s 工具链与扩展

| 组件 | 当前配置版本 | 最新稳定版 | 状态 |
|---|---|---|---|
| **Helm**（客户端） | 4.0.1 | **4.2.0** | Helm 4 于 2025-11 发布，4.2.0 为当前最新 |
| **helmify** | 未固定版本 | **0.4.20** | 建议在 packages 中固定版本 |
| **kubectl** | 未固定版本 | **1.36.1** | 与 Kubernetes 版本对齐 |
| **Docker CE**（mw1 本机） | 29.3.1 | **29.5.1** | 2026-05-18 发布，含安全修复 |
| **ingress-nginx** | 4.1.0（Helm chart） | controller-v1.15.1 | ⚠️ **社区版已于 2026-03-24 归档，不再接受 PR/安全修复**；建议迁移到其他 Ingress Controller |
| **Kuboard** | v3（未固定版本） | **v4.1.0.3** | 已发布 v4 大版本，升级需注意 API 兼容性 |

### 升级优先级建议

| 优先级 | 组件 | 原因 |
|---|---|---|
| 🔴 立即 | MySQL 8.0.28 | 8.0.x 已 EOL，不再接受安全修复 |
| 🔴 立即 | ingress-nginx | 社区版已归档，后续无安全修复；评估替代方案（如 [Gateway API](https://gateway-api.sigs.k8s.io/)、[Traefik](https://traefik.io/)） |
| 🟡 建议 | CNI plugins | 1.6.0 → 1.9.1 含安全更新 |
| 🟡 建议 | Docker CE | 29.3.1 → 29.5.1 含安全修复 |
| 🟢 可选 | Nexus 3.85 → 3.92.3 | 功能更新，无紧迫性 |
| 🟢 可选 | Kubernetes 1.31 → 1.36 | 仍在支持窗口，按业务节奏升级 |
| ⏳ 观望 | Rustfs | 等待正式 GA 版本后再评估生产使用 |

---