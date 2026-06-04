# datasophon-cli init kubectl

## 用途

在目标节点上安装 `kubectl` 二进制（直接拷贝可执行文件到 `/usr/bin/kubectl`）。若节点已安装 kubectl，跳过。当 `--enableKubernetesCluster` 为 `false` 时跳过。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init kubectl \
  --packagePath <dir> \
  --installPath <dir> \
  -x <x86_64-tar> \
  -a <aarch64-tar> \
  [--enableKubernetesCluster] \
  [公共 flag]
```

## 参数 / Flags

|            flag             |  简写  |   类型   |   默认   | 必填 |               说明                |
|-----------------------------|------|--------|--------|----|---------------------------------|
| `--enableKubernetesCluster` | 无    | bool   | `true` | 否  | 为 false 时跳过 kubectl 安装          |
| `--packagePath`             | 无    | string | —      | 是  | 安装包目录                           |
| `--installPath`             | 无    | string | —      | 是  | 安装根目录（kubectl 从此目录拷至 /usr/bin/） |
| `--x86Tar`                  | `-x` | string | —      | 是  | x86_64 kubectl 文件名              |
| `--aarch64Tar`              | `-a` | string | —      | 是  | aarch64 kubectl 文件名             |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

|                      字段                      |        说明         |
|----------------------------------------------|-------------------|
| `global.kubernetes.enable`                   | 若为 false，DAG 跳过此步 |
| `global.packages.kubectl.x86_64` / `aarch64` | 包文件名              |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init kubectl \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/kubectl \
  -x kubectl-linux-amd64 \
  -a kubectl-linux-arm64 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init kubectl \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/kubectl \
  -x kubectl-linux-amd64 \
  -a kubectl-linux-arm64 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|      情况       |   说明   |
|---------------|--------|-------------------------|
| `kubectl 已安装` | 跳过，不报错 |
| `安装包不存在`      | 文件名不匹配 | 确认 `-x` / `-a` 与实际文件名一致 |

## 相关命令

- [`init k8sBaseServices`](./k8sbaseservices.md) — 用 sealos 部署 K8s 集群
- [DAG 步骤表](../../../reference/init-all-dag.md)

