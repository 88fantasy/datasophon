# Docker Compose 测试环境

用于验证新架构（gRPC 注册/心跳/命令分发）的端到端测试环境，包含：

| 服务 | 说明 | 宿主机端口 |
|---|---|---|
| **mysql** | 数据库（DB 迁移由 Master 启动时自动执行） | 3307 |
| **nexus** | Sonatype Nexus 3 制品仓库（raw 仓库） | 8081 |
| **nexus-init** | 一次性容器，Nexus 就绪后创建 `raw` 仓库 | — |
| **master** | datasophon-api 进程（HTTP :8080 / gRPC :18081） | 8080, 18081 |
| **cluster-seed** | 一次性容器，Master 就绪后插入测试集群记录（id=1） | — |
| **worker** | datasophon-worker 进程（gRPC :18082），注册到 master | — |

启动顺序：`mysql` + `nexus` → `nexus-init` → `master` → `cluster-seed` → `worker`

## 前置准备

```bash
export JH17=/Users/pro/Library/Java/JavaVirtualMachines/jbr-17.0.12-1/Contents/Home
JAVA_HOME=$JH17 ./mvnw clean package -DskipTests -s ~/.m2/setting.xml
```

确认产物存在：
```bash
ls datasophon-api/target/datasophon-manager-2.1-SNAPSHOT.tar.gz
ls datasophon-worker/target/datasophon-worker.tar.gz
```

## 启动

```bash
cd deploy/compose
docker compose up --build
```

> ⚠️ Nexus 首次冷启动约 1-2 分钟，compose 会等待其健康检查通过后再启动 master。

## 验证

```bash
# Master HTTP
curl -sL -o /dev/null -w "HTTP %{http_code}" http://localhost:8080/ddh/
# → HTTP 200

# Nexus UI
open http://localhost:8081   # admin / admin123

# Worker 注册成功
docker compose logs master | grep -iE "Worker registered"
docker compose logs worker  | grep -iE "registered to master"

# 查看 Nexus raw 仓库
curl -su admin:admin123 http://localhost:8081/service/rest/v1/repositories/raw/hosted/raw

# 浏览器访问 datasophon UI
open http://localhost:8080/ddh   # admin / admin123
```

## 停止 & 清理

```bash
# 停止但保留 nexus-data volume（nexus 仓库数据不丢失）
docker compose down

# 停止并清除所有 volume（完全重置）
docker compose down -v
```
