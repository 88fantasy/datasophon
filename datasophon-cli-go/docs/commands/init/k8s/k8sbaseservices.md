# datasophon-cli init k8sBaseServices

## 用途

使用 sealos 在指定 master/worker 节点上部署 Kubernetes 集群（含 calico 网络插件、ingress-nginx 控制器、containerd 运行时）。这是 K8s 相关步骤中最核心的一步，执行时间较长（10–30 分钟）。当 `--enableKubernetesCluster` 为 `false` 时跳过；要求 worker 节点数量不少于 3 个。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init k8sBaseServices \
  --masters <host1,host2,...> \
  --nodes <host1,host2,...> \
  --packagePath <dir> \
  --sealos <filename> \
  --sealosX86Tar <filename> \
  --sealosArmTar <filename> \
  --kubernetes <filename> \
  --kubernetesX86Tar <filename> \
  --kubernetesArmTar <filename> \
  --helm <filename> \
  --helmTX86ar <filename> \
  --helmArmTar <filename> \
  [其他可选 flag] \
  [公共 flag]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--enableKubernetesCluster` | 无 | bool | `true` | 否 | 为 false 时跳过整个步骤 |
| `--kubernetesForce` | 无 | bool | `false` | 否 | 集群已存在时是否强制重建 |
| `--namespaces` | 无 | string | `""` | 否 | 额外创建的命名空间（逗号分隔） |
| `--masters` | 无 | string | — | 是 | Master 节点列表（逗号分隔的 hostname/IP） |
| `--nodes` | 无 | string | — | 是 | Worker 节点列表（逗号分隔，最少 3 个） |
| `--sealos` | 无 | string | — | 是 | sealos 二进制文件名 |
| `--sealosX86Tar` | 无 | string | — | 是 | x86_64 sealos 镜像 tar 包文件名 |
| `--sealosArmTar` | 无 | string | — | 是 | aarch64 sealos 镜像 tar 包文件名 |
| `--kubernetes` | 无 | string | — | 是 | kubernetes 镜像文件名 |
| `--kubernetesX86Tar` | 无 | string | — | 是 | x86_64 kubernetes tar 包文件名 |
| `--kubernetesArmTar` | 无 | string | — | 是 | aarch64 kubernetes tar 包文件名 |
| `--helm` | 无 | string | — | 是 | helm 二进制文件名 |
| `--helmTX86ar` | 无 | string | — | 是 | x86_64 helm tar 包文件名（注意：flag 名含 TX86，为代码原有拼写） |
| `--helmArmTar` | 无 | string | — | 是 | aarch64 helm tar 包文件名 |
| `--calico` | 无 | bool | `false` | 否 | 是否安装 calico 网络插件 |
| `--calicoX86Tar` | 无 | string | `""` | 否 | x86_64 calico tar 包文件名 |
| `--calicoArmTar` | 无 | string | `""` | 否 | aarch64 calico tar 包文件名 |
| `--ingress` | 无 | bool | `false` | 否 | 是否安装 ingress-nginx |
| `--ingressX86Tar` | 无 | string | `""` | 否 | x86_64 ingress tar 包文件名 |
| `--ingressArmTar` | 无 | string | `""` | 否 | aarch64 ingress tar 包文件名 |
| `--packagePath` | 无 | string | — | 是 | 安装包目录 |
| `--sshPort` | 无 | int | `22` | 否 | 节点 SSH 端口（sealos 使用） |
| `--sshPasswd` | 无 | string | `""` | 否 | 节点 SSH 密码（sealos 使用） |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

> **注意**：`--helmTX86ar` 中 "TX86" 是代码层面的历史拼写，不是 "Tar" + "X86" 的倒置写法，填写时请与其他节点的 `--helmArmTar` 对照使用。

## 配置文件依赖

| 字段 | 说明 |
|---|---|
| `global.kubernetes.enable` | 若为 false，DAG 跳过此步 |
| `global.kubernetes.masters` | Master 节点列表，DAG 自动传入 `--masters` |
| `global.kubernetes.nodes` | Worker 节点列表，DAG 自动传入 `--nodes` |
| `global.packages.sealos.*` | sealos 相关包文件名 |
| `global.packages.kubernetes.*` | K8s 相关包文件名 |

## 示例

### dry-run 预检（配置文件模式下通常由 DAG 自动填参）

```bash
datasophon-cli --dry-run init k8sBaseServices \
  --masters master01 \
  --nodes worker01,worker02,worker03 \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --sealos sealos \
  --sealosX86Tar sealos-4.3.7-amd64.tar.gz \
  --sealosArmTar sealos-4.3.7-arm64.tar.gz \
  --kubernetes kubernetes \
  --kubernetesX86Tar kubernetes-v1.29.4-amd64.tar.gz \
  --kubernetesArmTar kubernetes-v1.29.4-arm64.tar.gz \
  --helm helm \
  --helmTX86ar helm-v3.15.2-amd64.tar.gz \
  --helmArmTar helm-v3.15.2-arm64.tar.gz \
  --calico \
  --calicoX86Tar calico-v3.27.3-amd64.tar.gz \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `worker 节点数量不足 3 个` | `--nodes` 指定节点少于 3 个 | 增加 worker 节点 |
| sealos 执行失败 | 节点 SSH 不通或镜像包损坏 | 检查 `--sshPasswd` / `--sshPort`；验证包完整性 |

## 相关命令

- [`init k8sRegistryConf`](./k8sregistryconf.md) — 配置 containerd 私有仓库（需在此步之后执行）
- [`init kuboard`](./kuboard.md) — 安装 Kuboard 可视化管理界面
- [DAG 步骤表](../../../reference/init-all-dag.md)
