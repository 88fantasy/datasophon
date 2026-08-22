# Docker Compose 本地与测试环境

本目录提供 4 个独立的 Compose 入口：基础联调、可观测数据面、Kubernetes 拓扑模拟和 Standalone 拓扑模拟。它们用于开发、验证和演示，不等同于生产高可用部署。

## 入口总览

| 入口 | 用途 | 主要服务 |
|---|---|---|
| `docker-compose.yml` | 最小 Datasophon API/Worker 联调 | MySQL、Nexus、Master、Worker、seed |
| `docker-compose.observability.yml` | 本地 OTel → Doris 数据面 | Doris FE/BE、schema init、OTel Collector |
| `kubernetes/docker-compose.yml` | 模拟 1 个中间件节点 + 1 个 K8s Worker | MySQL、Nexus、MinIO、API、2 个 Worker |
| `standalone/docker-compose.yml` | 模拟 1 个中间件节点 + 2 个应用节点 | MySQL、Nexus、MinIO、API、3 个 Worker |

`Dockerfile.master` 和 `Dockerfile.worker` 由基础、K8s 和 Standalone 三套 Datasophon 环境共用。

## 前置准备

Compose 镜像从本地 Maven 产物构建，先在仓库根目录执行：

```bash
export JAVA_HOME=/path/to/jdk-21
./mvnw clean package -DskipTests

test -f datasophon-api/target/datasophon-manager-3.0-SNAPSHOT.tar.gz
test -f datasophon-worker/target/datasophon-worker.tar.gz
```

## 基础联调环境

文件：`deploy/compose/docker-compose.yml`。

| 服务 | 容器 | 宿主机端口 |
|---|---|---|
| MySQL 8 | `ddh-mysql` | `3307` |
| Nexus | `ddh-nexus` | `8081` |
| Datasophon API | `master` | HTTP `8080`、gRPC `18081`、JMX exporter `8586` |
| Datasophon Worker | `worker` | 仅 Compose 网络内使用 `18082` |

```bash
docker compose -f deploy/compose/docker-compose.yml up --build

curl -fsS http://127.0.0.1:8080/ddh/actuator/health
docker compose -f deploy/compose/docker-compose.yml logs master worker
```

Nexus 初始化和集群 seed 是一次性容器，正常结束后状态为 `Exited (0)`。

## 可观测数据面

文件：`deploy/compose/docker-compose.observability.yml`。该环境使用 Doris `4.0.6` 镜像验证当前 OTel schema 与查询链路，Collector 镜像为 `0.156.0`。

| 服务 | 宿主机端口 |
|---|---|
| Doris FE HTTP | `8030` |
| Doris FE MySQL | `9030` |
| Doris BE HTTP | `8040` |
| Collector self metrics | `8888` |
| OTLP gRPC / HTTP | `4317` / `4318` |

```bash
docker compose -f deploy/compose/docker-compose.observability.yml up -d
docker compose -f deploy/compose/docker-compose.observability.yml ps

mysql -h127.0.0.1 -P9030 -uroot -e 'SHOW DATABASES'
curl -fsS http://127.0.0.1:8888/metrics >/dev/null
```

配置和 Doris 初始化说明见 [observability-stack.md](./observability-stack.md)。

## Kubernetes 拓扑模拟

文件：`deploy/compose/kubernetes/docker-compose.yml`。该拓扑模拟中间件节点 `mw1` 和 K8s Pod 节点 `w1`，不创建真实 Kubernetes 集群。

| 容器 | 模拟对象 | 宿主机端口 |
|---|---|---|
| `ddh-k8s-mysql` | mw1 MySQL | `3308` |
| `ddh-k8s-nexus` | mw1 Nexus | `8081` |
| `ddh-k8s-minio` | Rustfs 的 S3 兼容替代 | `9040` / `9041` |
| `ddh-k8s-api` | Datasophon API | `8080` / `18081` |
| `ddh-k8s-mw-worker` | mw1 裸机 Worker | `18082` |
| `ddh-k8s-k8s-worker` | w1 K8s Worker | `18083` → 容器 `18082` |

```bash
cd deploy/compose/kubernetes
docker compose up --build

curl -fsS http://127.0.0.1:8080/ddh/actuator/health
docker compose logs mw-api mw-worker k8s-worker
```

详细场景见 [K8s 部署模拟说明](../deployment-k8s.md)。

## Standalone 拓扑模拟

文件：`deploy/compose/standalone/docker-compose.yml`。该拓扑模拟中间件节点 `mw1` 和应用节点 `app1`、`app2`。

| 容器 | 模拟对象 | 宿主机端口 |
|---|---|---|
| `ddh-sa-mysql` | mw1 MySQL | `3309` |
| `ddh-sa-nexus` | mw1 Nexus | `8093` |
| `ddh-sa-minio` | Rustfs 的 S3 兼容替代 | `9044` / `9045` |
| `ddh-sa-api` | Datasophon API | `8082` → `8080`、`18084` → `18081` |
| `ddh-sa-mw-worker` | mw1 Worker | `18085` → `18082` |
| `ddh-sa-app1-worker` | app1 Worker | `18086` → `18082` |
| `ddh-sa-app2-worker` | app2 Worker | `18087` → `18082` |

```bash
cd deploy/compose/standalone
docker compose up --build

curl -fsS http://127.0.0.1:8082/ddh/actuator/health
docker compose logs mw-api mw-worker app1-worker app2-worker
```

详细场景见 [Standalone 部署模拟说明](../deployment-standalone.md)。

## 并行运行时的端口

K8s 与 Standalone 两套拓扑的端口已错开，可以同时启动；基础环境与 K8s 环境都会占用 Nexus `8081`，不能同时启动。

| 服务 | 基础 | K8s 模拟 | Standalone 模拟 |
|---|---:|---:|---:|
| MySQL | `3307` | `3308` | `3309` |
| Nexus | `8081` | `8081` | `8093` |
| API HTTP | `8080` | `8080` | `8082` |
| API gRPC | `18081` | `18081` | `18084` |

可观测环境会占用 `8030`、`9030`、`8040`、`8888`、`4317` 和 `4318`；启动前确认没有与本机服务冲突。

## 停止与清理

在对应 Compose 目录执行，或显式指定文件：

```bash
# 停止并保留 volume
docker compose down

# 停止并删除该环境声明的 volume
docker compose down -v
```

`down -v` 会删除 MySQL、Nexus、MinIO 或 Doris 的本地测试数据；执行前确认当前目录和 Compose 文件正确。
