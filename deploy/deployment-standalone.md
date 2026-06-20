# Datasophon 离线 Standalone 部署指南

> 拓扑规格：1 middleware + 2 application（三节点最小大数据裸机集群）
> 适用于**无 Kubernetes** 的纯裸机大数据服务管理场景（HDFS / YARN / Hive / Kafka 等）
> 基于分支 `refactor/cluster-type`，配置文件真相之源：`datasophon-cli-go/internal/config/configs/cluster-config.yml`

---

## 注意事项

> 以下事项是在实际部署中容易犯错的地方，请在操作前仔细阅读。

1. **datasophon-api HTTP 端口为 8080，Nexus 使用默认的 8081，两者不冲突**
   datasophon-api 的 Spring Boot 默认端口即 8080，Nexus 保持默认端口 8081，部署在同一节点 mw1 上不会互相影响。
   Nexus Docker Registry 仍使用 8083。

2. **datasophon-worker 以 systemd 形式安装在所有节点上**
   mw1、app1、app2 均需裸机 datasophon-worker，管理各自节点上的服务（启停/安装）。
   Standalone 模式下不存在 K8s Pod 形态的 worker。

3. **app 节点的 `masterHost` 必须是 mw1 的实际 IP，不能用 `127.0.0.1`**
   mw1 自身的 worker 配置 `masterHost=127.0.0.1`；app1/app2 必须填写 mw1 可达的宿主机 IP。

4. **所有离线包须先上传到 Nexus:8081**
   app1/app2 通过 `http://mw1:8081/repository/yum/<arch>/<os>/` 安装 OS 依赖包；
   二进制安装包从 Nexus raw 仓库下发。部署前必须完成 `datasophon-cli init registry-upload`。

5. **NTP 先于所有业务服务部署**
   HDFS NameNode / ZooKeeper 等服务对节点时钟一致性要求严格（偏差 > 30s 可能引发脑裂）。
   确保 `ntpServer` 在所有服务安装前就绪。

6. **无需安装 containerd / kubelet 等 K8s 组件**
   cluster-config.yml 中 `kubernetes` 块应缺省或 `enable: false`；
   CLI 会跳过所有 `init-k8s-*` / `init-containerd` / `init-sealos` 步骤。

7. **cluster-config.yml 改动后需重新执行 `create cluster`**
   CLI 在启动时一次性 hash 整个 config，incremental 执行不会自动检测配置变更。

8. **Docker daemon 仅安装在需要容器化业务服务的节点**
   纯大数据节点（HDFS/YARN）通常不需要 Docker；需要时在对应 app 节点的 `k8sTools.docker: true` 下配置。

---

## 一、节点角色与组件清单

| 节点 | 角色 | 服务端组件 | 说明 |
|---|---|---|---|
| **mw1** | 管理端 + 中间件（CLI 操作入口） | **datasophon-api**（8080 HTTP + 18081 gRPC）/ **datasophon-worker 裸机**（18082）/ **Nexus**（8081 + 8083，含 yum 仓库）/ **MySQL**（3306）/ **NTP**（123/udp）/ **Rustfs**（9040 + 9041）/ Docker（unix socket）/ nmap | 不运行业务大数据服务 |
| **app1** | 应用节点 1 | **datasophon-worker 裸机**（18082）/ 业务服务（HDFS NameNode / YARN ResourceManager / Hive MetaStore 等，由 datasophon-api 管控安装）/ Docker（可选，unix socket） | datasophon-api 通过 gRPC 下发 Install/Start/Stop 命令 |
| **app2** | 应用节点 2 | **datasophon-worker 裸机**（18082）/ 业务服务（HDFS DataNode / YARN NodeManager 等）/ Docker（可选） | 同 app1 |

> **mw1 组件安装方式**：Nexus / MySQL / Rustfs 由 `datasophon-cli create cluster -a initALL` 通过 SSH 远程安装。datasophon-api 从 `datasophon-assembly` tar.gz 解压启动（不经过 CLI 管控）。datasophon-worker 以 systemd 方式手动部署（见六）。

---

## 二、端口速查表

### mw1（middleware 节点）

| 服务 | 端口/协议 | 说明 |
|---|---|---|
| datasophon-api HTTP | **8080**/TCP | 管理 UI 入口（路径 `/ddh`） |
| datasophon-api gRPC | **18081**/TCP | Worker 注册 / 心跳 / 反向回调入口 |
| datasophon-worker gRPC（裸机） | **18082**/TCP | API 向 mw1 本身下发 OS 级命令 |
| Nexus Web（yum/raw/helm） | **8081**/TCP | 制品库 UI；yum 源路径 `/repository/yum/<arch>/<os>/`；所有节点离线包从此拉取 |
| Nexus Docker Registry | **8083**/TCP | 容器镜像仓库（供 Docker 拉取私有镜像） |
| MySQL | **3306**/TCP | datasophon-api 业务 DB |
| Rustfs API（S3） | **9040**/TCP | S3 兼容对象存储 API |
| Rustfs Web UI | **9041**/TCP | 浏览器控制台 |
| NTP（chrony） | **123**/UDP | 集群所有节点 NTP 对时源 |
| Docker daemon | unix socket | `/run/docker.sock`，不监听 TCP |
| SSH | **22**/TCP | CLI 远程执行入口 |

### app1 / app2（应用节点）

| 服务 | 端口/协议 | 说明 |
|---|---|---|
| datasophon-worker gRPC（裸机） | **18082**/TCP | API 向此节点下发 Install/Start/Stop 命令 |
| Docker daemon（可选） | unix socket | `/run/docker.sock`，不监听 TCP |
| SSH | **22**/TCP | CLI 远程执行入口 |
| **业务服务端口** | 依服务而定 | HDFS NameNode 9000/50070、DataNode 50010/50075、YARN RM 8088、NM 8042、ZooKeeper 2181、Kafka 9092 等，由 datasophon-api 管控 |

---

## 三、关键网络连通性

| 方向 | 端口 | 用途 |
|---|---|---|
| app1, app2 → mw1:8081 | TCP | Nexus 仓库（yum 离线源 `/repository/yum/<arch>/<os>/` + raw 安装包） |
| app1, app2 → mw1:123 | UDP | chrony NTP 对时 |
| 外部 → mw1:8080 | TCP | datasophon-api 管理 UI |
| mw1 worker（裸机）→ 127.0.0.1:18081 | TCP | 注册 + 30s 心跳 |
| app1 worker → mw1:18081 | TCP | 注册 + 30s 心跳 |
| app2 worker → mw1:18081 | TCP | 注册 + 30s 心跳 |
| datasophon-api（mw1）→ 127.0.0.1:18082 | TCP | 下发命令给 mw1 自身 worker |
| datasophon-api（mw1）→ app1:18082 | TCP | 下发 Install/Start/Stop 命令 |
| datasophon-api（mw1）→ app2:18082 | TCP | 下发 Install/Start/Stop 命令 |
| app1 ↔ app2 | 业务端口 | HDFS/YARN 等节点间通信（NameNode↔DataNode、RM↔NM 等） |

---

## 四、ASCII 拓扑图

```
   ┌──────────────── External Network ────────────────┐
   │                                                  │
   │  Browser / Admin                                 │
   │                                                  │
   └─────────────────┬────────────────────────────────┘
                     │
                  8080/ddh
                  (DS-API UI)
                  9041 (Rustfs UI)
                     │
                     ▼
   ┌─────────────────────────────────────────────────────────┐
   │ mw1   Middleware + 管理端（CLI 操作入口）                 │
   │ ───────────────────────────────────────────────────────│
   │ ┌─────────────────────────┐  ┌──────────────────────┐  │
   │ │ datasophon-api          │  │ datasophon-worker    │  │
   │ │   :8080 HTTP (/ddh)     │◀▶│   (裸机 systemd)     │  │
   │ │   :18081 gRPC server    │  │   :18082 gRPC         │  │
   │ │   (接受所有 worker 注册) │  │   → 127.0.0.1:18081  │  │
   │ └────────┬────────────────┘  └──────────────────────┘  │
   │          │ JDBC 127.0.0.1                               │
   │          ▼                                              │
   │ Nexus Web        :8081  (yum/raw/helm；含 yum 离线源)   │
   │ Nexus Docker     :8083  (容器镜像仓库)                  │
   │ MySQL            :3306  ◀── localhost from API         │
   │ NTP (chronyd)    :123/udp ◀── 全集群对时               │
   │ Rustfs API (S3)  :9040                                  │
   │ Rustfs Web UI    :9041                                  │
   │ Docker daemon    (uds /run/docker.sock)                 │
   │ SSH              :22   ◀── CLI 远程执行入口             │
   └──────────────┬──────────────────────────────────────────┘
                  │ gRPC 命令下发
          ┌───────┴────────┐
          │                │
          ▼                ▼
   ┌──────────────┐  ┌──────────────┐
   │ app1         │  │ app2         │
   │ ─────────────│  │ ─────────────│
   │ ds-worker    │  │ ds-worker    │
   │   :18082     │  │   :18082     │
   │   → mw1:     │  │   → mw1:     │
   │     18081    │  │     18081    │
   │ 业务服务      │◀▶│ 业务服务      │
   │ (HDFS NN     │  │ (HDFS DN     │
   │  YARN RM     │  │  YARN NM     │
   │  Hive Meta   │  │  ZooKeeper…) │
   │  等)          │  │              │
   │ Docker(可选)  │  │ Docker(可选) │
   │ SSH :22      │  │ SSH :22      │
   └──────────────┘  └──────────────┘

  worker 注册 / 命令下发链路:
    [mw1 worker]   →  127.0.0.1:18081   注册 + 30s 心跳
    [app1 worker]  →  mw1:18081         注册 + 30s 心跳
    [app2 worker]  →  mw1:18081         注册 + 30s 心跳
    [mw1 api]      →  127.0.0.1:18082   下发命令（mw1 worker）
    [mw1 api]      →  app1:18082        下发命令（Install/Start/Stop）
    [mw1 api]      →  app2:18082        下发命令（Install/Start/Stop）

  基础设施链路:
    app1, app2  →  mw1:8081   Nexus 仓库（yum 离线源 + raw 安装包）
    app1, app2  →  mw1:123    chrony NTP 对时

  WorkerRegistry 视角（datasophon-api 内存表）:
    hostname=mw1   →  WorkerEndpoint{ip:127.0.0.1, port:18082}
    hostname=app1  →  WorkerEndpoint{ip:<app1-ip>, port:18082}
    hostname=app2  →  WorkerEndpoint{ip:<app2-ip>, port:18082}
```

---

## 五、cluster-config.yml 关键配置

基于 `datasophon-cli-go/internal/config/configs/cluster-config.yml` 的 sample，按三节点 standalone 拓扑做以下调整：

```yaml
global:
  cluster-type: bigdata   # 非 kubernetes 类型；IsKubernetes=false，跳过所有 K8s 步骤
  offline: true
  osInfo:
    auto: true
    osType: openEuler-22.03-LTS-SP3
    archType: x86_64
  sshAuthType: "AUTO"

# kubernetes 块完全省略，或设置 enable: false
# kubernetes:
#   enable: false

registry:
  enable: true
  node: "mw1"
  config:
    webPort: 8081            # Nexus 默认端口；datasophon-api 使用 8080，两者不冲突
    dockerHttpPort: 8083
    repositories: ["yum", "raw", "apt", "docker", "helm"]

yumServer:
  enable: false              # Nexus yum 仓库已接管离线源，不再安装 httpd/apache

mysql:
  enable: true
  node: "mw1"
  port: 3306

ntpServer:
  enable: true
  node: "mw1"

nmapServer:
  enable: true
  node: "mw1"

rustfs:
  enable: true
  nodes: ["mw1"]
  config:
    apiPort: 9040
    webPort: 9041
    installType: "SNSD"
    volumes: "/data/rustfs0"

nodes:
  - ip: <mw1-ip>
    user: "root"
    password: ""
    port: 22
    hostname: "mw1"
  - ip: <app1-ip>
    user: "root"
    password: ""
    port: 22
    hostname: "app1"
  - ip: <app2-ip>
    user: "root"
    password: ""
    port: 22
    hostname: "app2"
```

> datasophon-api 不受 CLI 管控，需从 `datasophon-assembly` 产出的 tar.gz 在 mw1 上手动解压启动。

### mw1 组件安装工作流

Nexus / MySQL / Rustfs 由 `datasophon-cli` 通过 SSH 远程安装，无需提前在 mw1 上操作：

```
┌─────────────────────────────────────────────────────────────────────┐
│  operator（可在任意有网络访问的机器上执行）                             │
│                                                                     │
│  1. 联网下载安装包（nexus tar.gz / mysql rpm-bundle / rustfs gz）   │
│     → 存放至 $DATASOPHON_INIT/packages/                             │
│  2. 编辑 cluster-config.yml（节点 IP、hostname、端口、密码）         │
│  3. datasophon-cli create cluster -a initALL \                     │
│       --config cluster-config.yml                                   │
│     CLI 自动通过 SSH 将包 sftp 到目标节点并远程执行安装脚本           │
└─────────────────────────────────────────────────────────────────────┘
```

CLI 执行顺序（Standalone 相关关键步骤）：

1. `init-ntp-server`：在 mw1 安装 chrony，配置为 NTP server
2. `init-ntp-slave`：在 app1/app2 配置 chrony 指向 mw1（确保时钟同步）
3. `init-registry`：安装 Nexus，保持默认端口 8081，启动后通过 REST API 创建 `yum / raw / apt / docker / helm` 仓库
4. `init-registry-upload`：将所有安装包上传到 Nexus raw 仓库
5. `init-offline-nodes`：在 app1/app2 配置 yum baseurl → `http://mw1:8081/repository/yum/<arch>/<os>/`
6. `init-mysql`：安装 MySQL 8，初始化 datasophon / hive 等数据库
7. `init-rustfs`：安装 Rustfs，启动 S3 服务（9040 API + 9041 Web）

> K8s 相关步骤（`init-sealos`、`init-containerd`、`init-k8s-base-services` 等）因 `IsKubernetes=false` 全部跳过。

---

## 六、datasophon-worker 部署模板（systemd，三节点通用）

所有节点均使用 systemd 裸机部署，差异仅在 `--master.host` 参数：

### mw1（master.host = 127.0.0.1）

```ini
# /etc/systemd/system/datasophon-worker.service
[Unit]
Description=Datasophon Worker (mw1, manages middleware services)
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

### app1 / app2（master.host = mw1 实际 IP）

```ini
# /etc/systemd/system/datasophon-worker.service
[Unit]
Description=Datasophon Worker (app node, manages big-data services)
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/datasophon/worker
ExecStart=/usr/bin/java -jar /opt/datasophon/worker/datasophon-worker.jar \
  --grpc.server.port=18082 \
  --master.host=<mw1-ip> \
  --master.grpc.port=18081
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# 三台节点通用启动命令
systemctl daemon-reload
systemctl enable --now datasophon-worker
systemctl status datasophon-worker
```

---

## 七、部署后验证清单

| # | 验证项 | 命令 | 期望结果 |
|---|---|---|---|
| 1 | mw1 端口 | `ss -lntup \| grep -E '8080\|8081\|8083\|3306\|9040\|9041\|18081\|18082'` | 全部 LISTEN |
| 2 | mw1 NTP | `ss -lnup \| grep ':123 '` | chronyd LISTEN |
| 3 | app NTP 同步 | 在 app1/app2：`chronyc sources` | reference 指向 mw1 |
| 4 | app worker 端口 | 在 app1/app2：`ss -lntp \| grep ':18082 '` | datasophon-worker LISTEN |
| 5 | Nexus 健康 | `curl -fs http://mw1:8081/service/rest/v1/status` | HTTP 200 |
| 6 | Nexus 仓库 | `curl -u admin:xxx http://mw1:8081/service/rest/v1/repositories \| jq '.[].name'` | 含 yum / raw / docker / helm |
| 7 | yum 离线源（app 节点） | 在 app1：`yum repolist` | 仅列出指向 `mw1:8081` 的仓库 |
| 8 | datasophon-api 健康 | `curl http://mw1:8080/ddh/actuator/health` | `{"status":"UP"}` |
| 9 | MySQL 联通 | `mysql -h mw1 -P 3306 -u datasophon -p` | 登录成功，列出 `datasophon` 库 |
| 10 | Rustfs S3 | `curl http://mw1:9040/` | 返回 S3 XML 错误页（API 已起） |
| 11 | mw1 worker 注册 | `grep "WorkerRegistry" <api-log>` | 出现 hostname=mw1 注册成功 |
| 12 | app1 worker 注册 | `grep "hostname=app1" <api-log>` | 出现 app1 注册条目 |
| 13 | app2 worker 注册 | `grep "hostname=app2" <api-log>` | 出现 app2 注册条目 |
| 14 | WorkerRegistry 完整 | DataSophon UI → 主机管理 | 列出 mw1 / app1 / app2 三台，状态正常 |
| 15 | 命令下发 | DataSophon UI 点击 app1 / app2 的 Ping 测试 | 两台均返回 OK（验证 api → appN:18082 gRPC 链路） |

---

## 八、本地模拟测试（Docker Compose）

> 在现有 `deploy/compose/docker-compose.topology.yml` 的基础上，去掉 K8s worker，增加 app 节点 worker：

**与 K8s topology compose 的差异**：

| 项目 | K8s topology | Standalone |
|---|---|---|
| `k8s-worker`（hostname=w1） | 有（模拟 K8s Pod worker） | **去掉** |
| `app1-worker`（hostname=app1） | 无 | **新增**，复用 Dockerfile.worker |
| `app2-worker`（hostname=app2） | 无 | **新增**，复用 Dockerfile.worker |
| mw-api 配置 | topology-common.properties | 同（相同配置） |
| worker 配置 | topology-worker.properties | 同（masterHost=mw-api） |

核心 compose 片段（新增节点部分）：

```yaml
  app1-worker:
    build:
      context: ../..
      dockerfile: deploy/compose/Dockerfile.worker
    container_name: ddh-sa-app1-worker
    hostname: app1
    depends_on:
      cluster-seed:
        condition: service_completed_successfully
    environment:
      TZ: Asia/Shanghai
    volumes:
      - ./conf/topology-worker.properties:/datasophon-worker/conf/common.properties:ro
    restart: on-failure
    networks: [datasophon-net]

  app2-worker:
    build:
      context: ../..
      dockerfile: deploy/compose/Dockerfile.worker
    container_name: ddh-sa-app2-worker
    hostname: app2
    depends_on:
      cluster-seed:
        condition: service_completed_successfully
    environment:
      TZ: Asia/Shanghai
    volumes:
      - ./conf/topology-worker.properties:/datasophon-worker/conf/common.properties:ro
    restart: on-failure
    networks: [datasophon-net]
```

### 启动与验证

```bash
cd deploy/compose
# 启动（基于 topology compose，去掉 k8s-worker 后启动）
docker compose -f docker-compose.standalone.yml up --build

# 验证三台 worker 注册
docker compose -f docker-compose.standalone.yml logs mw-api | grep -iE "WorkerRegistry"

# 期望输出中出现 hostname=mw1 / hostname=app1 / hostname=app2 三条注册记录
```

---

## 九、关键文件参考

| 用途 | 文件 |
|---|---|
| 端口配置真相之源 | `datasophon-cli-go/internal/config/configs/cluster-config.yml` |
| ClusterType / IsKubernetes 定义 | `datasophon-cli-go/internal/config/global.go` |
| K8s 步骤条件（IsKubernetes guard） | `datasophon-cli-go/internal/plan/builders_cluster.go` |
| Nexus yum 仓库创建逻辑 | `datasophon-cli-go/internal/plan/registry_task.go::yumRepoCreate` |
| 节点 yum 源配置逻辑（Nexus 路径） | `datasophon-cli-go/internal/cli/init/offline_slave.go:43-73` |
| offlineServer 跳过逻辑（EnableRegistry guard） | `datasophon-cli-go/internal/cli/init/offline_server.go` |
| gRPC 端口常量 | `datasophon-grpc-api/.../GrpcConstants.java` |
| Worker 注册流程 | `datasophon-worker/.../MasterRegistryClient.java` |

---

## 十、组件版本参考

> 查询时间：2026-06-02。"当前配置版本"来自 `meta/datacluster/<SERVICE>/service_ddl.json` 的 `version` 字段；"最新稳定版"来自各官方仓库 Release 页。
> 状态说明：🚫 EOL/严重落后（需立即处理）· ⚠️ 可升级 · ✅ 较新

### mw1 中间件组件

| 组件 | 当前配置版本 | 最新稳定版 | 状态 |
|---|---|---|---|
| **MySQL** | 8.0.28 | **8.4.9 LTS** | ⚠️ 8.0.x 已于 2026-04 EOL，**强烈建议升级到 8.4 LTS** |
| **Nexus Repository 3** | 3.85.0 | **3.92.3** | ⚠️ 可升级，Nexus 2 已于 2025-06-30 停服 |
| **Rustfs** | 1.0.0 | 1.0.0-beta.6 | ℹ️ 仍在 Beta，GA 尚未正式发布；生产谨慎评估 |

### 大数据服务组件（meta/datacluster）

#### 存储 / 数据库

| 组件 | 当前配置版本 | 最新稳定版 | 状态 |
|---|---|---|---|
| **HDFS**（Hadoop） | **3.5.0** | **3.5.0** | ✅ 已升级至最新稳定版 |
| **YARN**（Hadoop） | **3.5.0** | **3.5.0** | ✅ 已升级，与 HDFS 版本保持一致 |
| **Hive** | **4.2.0** | **4.2.0** | ✅ 已升级；4.x 要求 JDK 21，部署前需确认运行环境 |
| **Elasticsearch** | **9.4.2** | **9.4.2** | ✅ 已升级至最新稳定版 |
| **Valkey** | **8.1.7** | **8.1.7**（Redis 的 BSD-3 开源分叉） | ✅ 已替换 Redis |
| **JuiceFS** | **1.3.1** | **1.3.1**（LTS，24 个月维护） | ✅ 已升级至 LTS 版本 |
| **Doris** | **4.0.5** | **4.0.5**（稳定）/ 4.1.1（最新） | ✅ 已升级；4.x 引入 AI/向量搜索，升级前充分测试 |

#### 计算 / 查询引擎

| 组件 | 当前配置版本 | 最新稳定版 | 状态 |
|---|---|---|---|
| **Spark3** | **3.5.8** | **3.5.8**（3.x LTS，安全维护至 2027-11）/ 4.1.2（最新） | ✅ 已升级至 3.x LTS |
| **Flink** | **2.2.1** | **2.2.1** | ✅ 已升级；1.x → 2.x 为不兼容大版本升级，上线前需验证 SQL API |
| **Kyuubi** | **1.11.1** | **1.11.1**（2026-03-26） | ✅ 已升级，支持 Spark 4.0/4.1 |

#### 消息 / 协调

| 组件 | 当前配置版本 | 最新稳定版 | 状态 |
|---|---|---|---|
| **Kafka** | **4.3.0** | **4.3.0** | ✅ 已升级；4.x 默认 KRaft 模式，已移除 ZooKeeper 依赖 |
| **ZooKeeper** | **3.8.6** | **3.9.5**（当前）/ **3.8.6**（稳定维护版） | ✅ 已升级至 3.8 稳定线最新版 |

#### 调度

| 组件 | 当前配置版本 | 最新稳定版 | 状态 |
|---|---|---|---|
| **DS**（DolphinScheduler） | **3.4.1** | **3.4.1**（2026-03-01） | ✅ 已是最新稳定版；包名 `apache-dolphinscheduler-3.4.1-bin.tar.gz`，无架构区分 |

#### 可观测性

| 组件 | 当前配置版本 | 最新稳定版 | 状态 |
|---|---|---|---|
| **Prometheus** | **3.12.0** | **3.12.0**（2026-05-28） | ✅ 已升级至最新稳定版 |
| **Alertmanager** | **0.32.1** | **0.32.1**（2026-04-29） | ✅ 已升级 |
| **Grafana** | **13.0.1** | **13.0.1**（2026-05） | ✅ 已升级 |
| **Loki** | **3.7.2** | **3.7.2**（2026-05-13） | ✅ 已升级 |
| **Promtail** | 2.8.11 | **已于 2026-03-02 EOL** | 🚫 **官方建议迁移至 Grafana Alloy；不再提供任何更新** |

#### 网关 / 注册中心

| 组件 | 当前配置版本 | 最新稳定版 | 状态 | 
|---|---|---|---|
| **APISIX** | **3.16.0** | **3.16.0**（2026-04-07） | ✅ 已升级；2.x → 3.x 配置格式有变化，升级前参阅迁移指南 |
| **Nacos** | **3.2.2** | **3.2.2** | ✅ 已升级；3.x 默认启用鉴权，升级需同步更新客户端 |
| **Nginx** | **1.30.2** | **1.30.2**（稳定线）/ 1.31.1（主线） | ✅ 已升级至最新稳定线 |

#### 内部组件（DataSophon 自研，无公开版本对比）

| 组件 | 当前配置版本 | 说明 |
|---|---|---|
| **DATART** | 3.6.1 | 数据可视化，DataSophon 内部打包 |
