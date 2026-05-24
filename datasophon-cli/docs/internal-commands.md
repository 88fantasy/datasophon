# 内部命令说明

以下 9 个 `Init*` 类**未注册到 `Init.java` 的 `subcommands` 列表**，因此无法通过 `java -jar datasophon-cli.jar init <name>` 直接调用。尝试调用会报错：

```
Unmatched argument at index 1: '<name>'
```

这些命令仅供 `create cluster` 在编排中内部实例化并通过 SSH 执行，**用户不应直接使用**。

---

## 内部命令列表

| 类名 | picocli 命令名 | 用途 | 在 `create cluster` 中的调用场景 |
|---|---|---|---|
| `InitDocker` | `docker` | 安装 Docker Engine（二进制方式） | `initALL` 中，k8s 启用时在本地 Master 安装 Docker，登录 Nexus Docker Registry |
| `InitHelm` | — | 安装 Helm CLI | K8s 安装流程中，在本地 Master 安装 Helm |
| `InitHelmify` | — | 安装 Helmify 工具 | K8s 安装流程中，在本地 Master 安装 Helmify |
| `InitKubectl` | — | 安装 kubectl | K8s 安装流程中，在本地 Master 安装 kubectl |
| `InitK8sBaseServices` | — | 通过 Sealos 部署 K8s 集群基础服务（含 Calico/Ingress/Helm） | `initALL` 中，在 K8s master[0] 上执行，完成整个 K8s 集群初始化 |
| `InitK8sKuboard` | — | 安装 Kuboard K8s 管理面板 | `initALL` 中，在 `global.kubernetes.kuboardI.node` 节点执行 |
| `InitK8sRegistryConf` | — | 配置 K8s 节点信任 Nexus Docker 私仓（insecure-registry） | `initALL` 中，在所有 K8s masters + nodes 执行 |
| `InitNtpSlave` | `ntpslave` | 配置节点将时钟同步到 NTP 服务端（chrony） | `initALL`/`initSingleNode` 中，在所有非 NTP 服务端节点执行 |
| `InitSsh` | `ssh` | 检测并建立 SSH 免密登录（基于 pssh/expect） | 未被 `create cluster` 调用，为独立历史遗留工具，功能部分完成 |

---

## 各命令关键参数（供阅读源码参考）

### InitDocker

**源文件**：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitDocker.java`

- 从 `packagePath` 解压 docker 二进制包到 `/usr/bin/`
- 生成 `/etc/systemd/system/docker.service`
- 配置 `/etc/docker/daemon.json`（`insecure-registries` 指向 Nexus）
- 生成 `/root/.docker/config.json`（Nexus 登录凭据）
- 检测 Docker API 版本，已安装且 `-kubernetesForce=false` 时跳过

### InitK8sBaseServices

**源文件**：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitK8sBaseServices.java`

- 使用 [Sealos](https://sealos.io/) 一键部署 Kubernetes 集群
- 支持 Calico CNI、Nginx Ingress、Helm 等附加组件
- `sealos run` 命令需要访问 Nexus Docker 私仓中的镜像

### InitNtpSlave

**源文件**：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitNtpSlave.java`

- 关键参数：`-ip / --ntpServerIp`（NTP 服务端 IP）
- 向 chrony 配置写入 `server <ntpServerIp> iburst`

### InitSsh

**源文件**：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitSsh.java`

- 历史遗留工具，依赖 pssh/tcl/expect（仅支持 CentOS 7/8/OpenEuler）
- 检测节点是否已免密，未免密则尝试安装 expect 并建立
- 代码中 `OsType os = null`（存在空指针风险），未被主流程调用

---

## 注意事项

- 若需手动调试内部命令，可在 `Init.java` 的 `subcommands` 列表中临时添加对应类，重新编译后即可直接调用。
- `InitK8sBaseServices` 依赖 Sealos 部署，需要本地 Master 对 K8s 所有节点有 SSH 访问权限且 `/etc/hosts` 配置正确。
