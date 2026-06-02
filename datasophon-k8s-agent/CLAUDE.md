# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## datasophon-k8s-agent

`datasophon-k8s-agent` 是 Datasophon 在 K8s 集群内运行的 Spring Boot Web Pod,负责接收 Master 通过 HTTP 发起的远端执行请求(RSA 签名鉴权),落地 `kubectl` / Helm 等 K8s 操作。架构定位详见 `docs/ARCHITECTURE.md` 第 4.7 节「K8s Agent」。

### 1. 常用命令

所有命令都在仓库根目录执行,通过 `-pl datasophon-k8s-agent -am` 仅构建该模块及其依赖。

```bash
# 编译(单独模块)
./mvnw -pl datasophon-k8s-agent -am clean compile

# 打 release tar.gz(assemble/assembly.xml:lib + conf + bin + release.txt)
./mvnw -pl datasophon-k8s-agent -am clean package

# 打 Docker 镜像包:生成 datasophon-k8s-agent-docker.tar.gz 并调用 docker/build.sh
./mvnw -pl datasophon-k8s-agent -am clean package -Pdocker

# 仅打多架构镜像(linux/amd64 + linux/arm64,需 docker buildx)
bash datasophon-k8s-agent/docker/build.sh <tag> --arch all
# 或:bash datasophon-k8s-agent/docker/build.sh <tag> --arch amd64 --proxy http://localhost:7890

# 打 Helm Chart:在 package 阶段自动调用 exec-maven-plugin tar 出 datasophon-k8s-agent-0.1.0.tgz
./mvnw -pl datasophon-k8s-agent -am package
# 或手动:
bash datasophon-k8s-agent/helm/build_helm.sh
# 安装/卸载:
helm install <release> datasophon-k8s-agent/target/datasophon-k8s-agent-0.1.0.tgz
helm uninstall <release>

# 运行测试(单元测试覆盖 SignatureVerifier、K8sAgentAuthInterceptor、DemoController、AgentApplication)
./mvnw -pl datasophon-k8s-agent -am test
```

### 2. 模块职责

- 部署形态:通常以 Deployment(默认 `replicaCount: 1`)部署在 K8s 集群内;如需每节点一个,可改 DaemonSet。Service 默认 `NodePort: 32552` 指向容器 `12552`。
- 暴露三类入口:
  - `/api/v1/health`、`/api/v1/ready` —— 存活/就绪探针,**不走签名鉴权**。
  - `/api/demo/echo` —— 演示接口(POST),仅用于连通性验证。
  - `/api/v1/**` 业务接口 —— **强制签名鉴权**,由 Master 触发实际的 K8s 操作。
- 鉴权模式:Master 持 RSA 私钥,Agent 持公钥。Master 用 `timestamp + nonce` 拼接待签字符串,RSA-SHA256 签名后随请求头下发;Agent 用 `SignatureVerifier` + `K8sAgentAuthInterceptor` 在 `preHandle` 校验,并做 `replay window` 拒绝过期请求。
- 与 Master 的接口约定:HTTP + 三个签名头(`timestamp`、`nonce`、`signature`,`Base64` 编码),约定见 `com.datasophon.common.K8sAgentAuthConstants`(在 `datasophon-common` 模块内)。本地开发时可在 `common.properties` 设 `k8s.agent.auth.enabled=false` 关闭鉴权。
- 统一响应:所有 JSON 返回值经 `UniResponseBodyWrapperAdvice` 包装为 `com.datasophon.common.utils.Result`,不再让 Controller 直接返回原始对象。

### 3. 包结构与关键文件

```
src/main/java/com/datasophon/k8sagent/
├── DatasophonK8sAgentApplication.java       # @SpringBootApplication 入口
├── config/
│   └── K8sAgentWebConfiguration.java        # 注册 K8sAgentAuthInterceptor,exclude /v1/health,/v1/ready,/error
├── auth/
│   ├── SignatureVerifier.java               # RSA-SHA256 校验工具(公钥构造 + 验签)
│   └── K8sAgentAuthInterceptor.java         # HandlerInterceptor:读 header → 校验时间窗 → 验签 → 失败写 401
├── aop/
│   ├── UniResponseBodyWrapperAdvice.java    # @ControllerAdvice 统一包成 Result.success(body)
│   └── GlobalExceptionHandler.java          # @RestControllerAdvice 统一异常 → Result.error
├── controller/
│   ├── probe/HealthController.java          # /v1/health、/v1/ready
│   └── demo/DemoController.java             # /demo/echo
└── dto/EchoDTO.java                         # 演示接口请求体
```

```
src/main/resources/
└── application.yaml                         # server.port=12552、spring.mvc.servlet.path=/api
```

```
docker/
├── Dockerfile                               # 镜像构建入口(基于 eclipse-temurin:17-jre-ubi9-minimal)
├── build.sh                                 # 调 docker buildx,支持 --arch all/amd64/arm64、--proxy
└── build.bat                                # Windows 镜像构建

build-resources/
├── Dockerfile                               # 备用 Dockerfile(本机 build,直接 COPY 整个上下文)
├── release.txt                              # 由 maven-resources-plugin 渲染后塞进 tar.gz 根目录
├── .dockerignore
└── bin/datasophon-k8sagent.sh               # start|stop|status|restart;前台/后台由 IS_DOCKER 决定

helm/datasophon-k8s-agent/
├── Chart.yaml                               # version 0.1.0、appVersion 2.1.0
├── values.yaml                              # image、Service、Probe、ConfigMap 默认值
├── .helmignore
└── templates/
    ├── deployment.yaml                      # 挂 ConfigMap 到 conf/application.yaml、conf/common.properties
    ├── service.yaml                         # NodePort 暴露 12552 → 32552
    ├── configmap.yaml                       # application.yaml + common.properties
    ├── _helpers.tpl
    ├── NOTES.txt
    └── tests/test-connection.yaml           # helm test 连通性用例

assemble/
├── assembly.xml                             # 普通 release tar.gz(includeBaseDirectory=true,带 release.txt)
└── assembly-docker.xml                      # Docker 镜像用 tar.gz(无 baseDirectory,直接平铺)
```

### 4. 关键约定(修改前必读)

- 所有 Controller 不要再 `return new Result<>(...)` 自己封装,`UniResponseBodyWrapperAdvice` 会自动包;若返回 `String` 类型,需由 advice 二次 JSON 化(已处理)。**唯一例外**:在 `ResponseBodyAdvice.supports` 中已排除 `Result` 类型。
- 新增业务接口必须挂到 `/v1/**` 或更具体路径下,使其走 `K8sAgentAuthInterceptor`(`/**` 拦截);`/v1/health`、`/v1/ready`、`/error` 已在 `K8sAgentWebConfiguration` 显式排除。**不要**绕过签名校验,也不要加 `@Anonymous` 之类的注解 — 当前鉴权是路径白名单模式,新增公开接口需要同步更新白名单。
- 业务路径前缀:`spring.mvc.servlet.path=/api` 写在 `application.yaml`;`/v1/health` 实际对外路径是 `/api/v1/health`(Helm `values.yaml` 中的 `livenessProbe`/`readinessProbe` 路径已经拼好)。
- 公钥来源:`k8s.agent.auth.public.key`(`common.properties` 注入,经 `PropertyUtils` 读取);`k8s.agent.auth.enabled=false` 时整个拦截器短路放行,只在本地/集成测试环境使用。生产环境**必须**打开。
- 错误响应统一用 `Result.error(code, message)`;`K8sAgentAuthInterceptor` 在签名失败时直接写 `401 + JSON`,不再交给 `GlobalExceptionHandler`。
- 镜像构建产物位置:`target/datasophon-k8s-agent-docker.tar.gz`,由 `maven-assembly-plugin` + `docker-unix`/`docker-windows` profile 串联 `build.sh`/`build.bat` 出 `vos/datasophon-k8s-agent:<tag>`。
- Helm Chart 产物位置:`target/datasophon-k8s-agent-0.1.0.tgz`,由 `exec-maven-plugin` 直接调 `tar czf`;`helm/build_helm.sh` 是等价的独立脚本。
- 启动脚本 `bin/datasophon-k8sagent.sh` 在容器中(`IS_DOCKER=true`)前台执行,否则 `nohup` 后台启动;`DEBUG=1` 启用 JDWP `:30105`(Dockerfile 已 `EXPOSE 30105`)。

### 5. 部署形态

- 镜像:`eclipse-temurin:17-jre-ubi9-minimal`,`AGENT_HOME=/opt/datasophon-k8s-agent`,`ENTRYPOINT=["bin/datasophon-k8sagent.sh","start"]`,暴露 `12552`(HTTP)与 `30105`(JDWP 调试)。
- K8s:Helm 默认 `Deployment` + `Service(NodePort)`,挂 `ConfigMap` 提供 `application.yaml` 与 `common.properties`;探针路径 `/api/v1/health`、`/api/v1/ready`。**实际投产时**应:① 关闭 `image.pullPolicy: Always`、固定 `tag`;② 替换为 `ClusterIP` + Ingress,避免在公网暴露签名接口;③ 通过 Secret 注入公钥,而不是 ConfigMap。
- 部署完成后,Master 侧 `datasophon-api` 通过 `K8SDAGExecutor` 在 K8s 集群编排时,会向 Agent 发起签名请求;Agent 是 "K8s 内部远端执行" 的最小化鉴权边界,不必打通业务网络。
