# Docker Compose 测试环境

本目录包含两套拓扑模拟环境，以及共享的 Dockerfile。

## 目录结构

```
compose/
├── Dockerfile.master          # datasophon-api 镜像（两套环境共用）
├── Dockerfile.worker          # datasophon-worker 镜像（两套环境共用）
├── kubernetes/                # K8s 部署拓扑模拟（1mw + 1 裸机 worker + 1 K8s Pod worker）
│   ├── docker-compose.yml
│   ├── conf/
│   │   ├── common.properties       # mw-api 配置
│   │   ├── worker.properties       # mw-worker / k8s-worker 配置
│   │   └── application-config.yml
│   └── seed/01-cluster.sql
└── standalone/                # Standalone 部署拓扑模拟（1mw + 2 裸机 app worker）
    ├── docker-compose.yml
    ├── conf/
    │   ├── common.properties       # mw-api 配置
    │   ├── worker.properties       # mw-worker / app1-worker / app2-worker 配置
    │   └── application-config.yml
    └── seed/01-cluster.sql
```

## 前置准备（两套环境通用）

```bash
export JH17=/Users/pro/Library/Java/JavaVirtualMachines/jbr-17.0.12-1/Contents/Home
JAVA_HOME=$JH17 ./mvnw clean package -DskipTests -s ~/.m2/setting.xml

# 确认产物
ls datasophon-api/target/datasophon-manager-3.0-SNAPSHOT.tar.gz
ls datasophon-worker/target/datasophon-worker.tar.gz
```

---

## kubernetes/  — K8s 拓扑模拟

> 详细文档：[deploy/deployment-k8s.md](../deployment-k8s.md)

模拟三节点 K8s 部署：mw1（中间件 + 裸机 worker）+ w1（K8s Pod worker）

| 容器 | 模拟对象 | 宿主机端口 |
|---|---|---|
| `ddh-k8s-mysql` | mw1 MySQL | **3308** |
| `ddh-k8s-nexus` | mw1 Nexus（制品库） | **8081** |
| `ddh-k8s-nexus-init` | Nexus 初始化（yum/raw/docker/helm，一次性） | — |
| `ddh-k8s-minio` | mw1 Rustfs（MinIO S3 兼容替代） | **9040 / 9041** |
| `ddh-k8s-api` | mw1 datasophon-api | **8081 / 18081** |
| `ddh-k8s-seed` | 集群记录初始化（一次性） | — |
| `ddh-k8s-mw-worker` | mw1 裸机 worker（hostname=mw1） | **18082** |
| `ddh-k8s-k8s-worker` | K8s Pod worker（hostname=w1） | **18083** |

```bash
cd deploy/compose/kubernetes
docker compose up --build

# 验证
curl http://localhost:8080/ddh/actuator/health
docker compose logs mw-api | grep -i "WorkerRegistry"   # 期望出现 mw1 + w1 两条注册记录
```

---

## standalone/  — Standalone 拓扑模拟

> 详细文档：[deploy/deployment-standalone.md](../deployment-standalone.md)

模拟三节点纯裸机部署：mw1（中间件 + 裸机 worker）+ app1/app2（应用节点 worker）

| 容器 | 模拟对象 | 宿主机端口 |
|---|---|---|
| `ddh-sa-mysql` | mw1 MySQL | **3309** |
| `ddh-sa-nexus` | mw1 Nexus（制品库） | **8093** |
| `ddh-sa-nexus-init` | Nexus 初始化（yum/raw，一次性） | — |
| `ddh-sa-minio` | mw1 Rustfs（MinIO S3 兼容替代） | **9044 / 9045** |
| `ddh-sa-api` | mw1 datasophon-api | **8082 / 18084** |
| `ddh-sa-seed` | 集群记录初始化（一次性） | — |
| `ddh-sa-mw-worker` | mw1 裸机 worker（hostname=mw1） | **18085** |
| `ddh-sa-app1-worker` | app1 裸机 worker（hostname=app1） | **18086** |
| `ddh-sa-app2-worker` | app2 裸机 worker（hostname=app2） | **18087** |

```bash
cd deploy/compose/standalone
docker compose up --build

# 验证
curl http://localhost:8082/ddh/actuator/health
docker compose logs mw-api | grep -i "WorkerRegistry"   # 期望出现 mw1 + app1 + app2 三条注册记录
```

---

## 两套环境端口对照（可同时运行）

| 服务 | kubernetes/ | standalone/ |
|---|---|---|
| MySQL | 3308 | 3309 |
| Nexus Web | 8081 | 8093 |
| MinIO/Rustfs API | 9040 | 9044 |
| MinIO/Rustfs Web | 9041 | 9045 |
| datasophon-api HTTP | 8080 | 8082 |
| datasophon-api gRPC | 18081 | 18084 |
| mw1 worker gRPC | 18082 | 18085 |
| worker-2 gRPC | 18083（k8s-worker/w1） | 18086（app1-worker） |
| worker-3 gRPC | — | 18087（app2-worker） |

> ⚠️ Nexus 首次冷启动约 1-2 分钟，compose 会等待健康检查通过后再启动后续服务。

## 停止 & 清理

```bash
# 停止但保留 volume（nexus/minio 数据不丢失）
docker compose down

# 停止并清除所有 volume（完全重置）
docker compose down -v
```
