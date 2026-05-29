# datasophon-cli init docker

## 用途

在目标节点上安装 Docker（离线二进制方式），写入 `docker.service`，配置 insecure-registry 指向私有 Nexus，并执行 `docker login` 验证连通性。当 `--enableKubernetesCluster` 为 `false` 时跳过。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init docker \
  --packagePath <dir> \
  --installPath <dir> \
  -x <x86_64-tar> \
  -a <aarch64-tar> \
  --dockerHttpPort <port> \
  [--enableKubernetesCluster] [--kubernetesForce] \
  [公共 flag]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--enableKubernetesCluster` | 无 | bool | `true` | 否 | 为 false 时跳过 Docker 安装 |
| `--packagePath` | 无 | string | — | 是 | 安装包目录 |
| `--installPath` | 无 | string | — | 是 | 安装根目录 |
| `--x86Tar` | `-x` | string | — | 是 | x86_64 Docker tar 包文件名 |
| `--aarch64Tar` | `-a` | string | — | 是 | aarch64 Docker tar 包文件名 |
| `--dockerHttpPort` | 无 | int | — | 是 | 私有 Docker Registry HTTP 端口（如 `8083`） |
| `--kubernetesForce` | 无 | bool | `false` | 否 | Docker 已存在时是否强制重装 |

> 继承 init 公共 flag（`--config`、`--registryIp`、`--registryUsername`、`--registryPassword` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

> **注意**：`--registryIp`、`--registryUsername`、`--registryPassword` 来自公共 flag，用于配置 insecure-registry 和 `docker login`。

## 配置文件依赖

| 字段 | 说明 |
|---|---|
| `global.kubernetes.enable` | 若为 false，DAG 跳过此步 |
| `global.registry.config.dockerHttpPort` | DAG 自动传入 `--dockerHttpPort` |
| `global.packages.docker.x86_64` / `aarch64` | 包文件名，DAG 传入 `-x` / `-a` |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init docker \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/docker \
  -x docker-26.1.4-x86_64.tar.gz \
  -a docker-26.1.4-aarch64.tar.gz \
  --dockerHttpPort 8083 \
  --registryIp 192.168.1.10 \
  --registryUsername admin \
  --registryPassword 'Nexus@123' \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init docker \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/docker \
  -x docker-26.1.4-x86_64.tar.gz \
  -a docker-26.1.4-aarch64.tar.gz \
  --dockerHttpPort 8083 \
  --registryIp 192.168.1.10 \
  --registryUsername admin \
  --registryPassword 'Nexus@123' \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `k8s 集群安装未开启，跳过 docker 安装` | `--enableKubernetesCluster=false` | 正常，无需处理 |
| `docker 安装失败` | systemctl start docker 失败 | 检查包完整性；确认 /usr/bin/dockerd 已拷贝 |
| `docker login 失败` | Nexus Docker Registry 不可达 | 确认 `--dockerHttpPort` 正确，Nexus 已启动 |

## 相关命令

- [`init k8sBaseServices`](./k8sbaseservices.md) — 安装 K8s 集群（依赖 docker）
- [`init k8sRegistryConf`](./k8sregistryconf.md) — 配置 containerd 的私有仓库认证
- [DAG 步骤表](../../../reference/init-all-dag.md)
