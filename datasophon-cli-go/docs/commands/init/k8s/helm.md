# datasophon-cli init helm

## 用途

在目标节点上安装 Helm（解压 tar 包并拷贝二进制到 `/usr/bin/`）。若节点已安装 Helm，跳过。当 `--kubernetesCluster` 为 `false` 时跳过。

> **注意**：此命令的 enable 开关 flag 名为 `--kubernetesCluster`（无 `enable` 前缀），与 `docker`/`kubectl` 等命令的 `--enableKubernetesCluster` 不同，请勿混淆。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init helm \
  --packagePath <dir> \
  --installPath <dir> \
  -x <x86_64-tar> \
  -a <aarch64-tar> \
  [--kubernetesCluster] \
  [公共 flag]
```

## 参数 / Flags

|         flag          |  简写  |   类型   |   默认   | 必填 |          说明           |
|-----------------------|------|--------|--------|----|-----------------------|
| `--kubernetesCluster` | 无    | bool   | `true` | 否  | 为 false 时跳过 Helm 安装   |
| `--packagePath`       | 无    | string | —      | 是  | 安装包目录                 |
| `--installPath`       | 无    | string | —      | 是  | 安装根目录                 |
| `--x86Tar`            | `-x` | string | —      | 是  | x86_64 Helm tar 包文件名  |
| `--aarch64Tar`        | `-a` | string | —      | 是  | aarch64 Helm tar 包文件名 |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

|                    字段                     |        说明         |
|-------------------------------------------|-------------------|
| `global.kubernetes.enable`                | 若为 false，DAG 跳过此步 |
| `global.packages.helm.x86_64` / `aarch64` | 包文件名              |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init helm \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/helm \
  -x helm-v3.15.2-linux-amd64.tar.gz \
  -a helm-v3.15.2-linux-arm64.tar.gz \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init helm \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/helm \
  -x helm-v3.15.2-linux-amd64.tar.gz \
  -a helm-v3.15.2-linux-arm64.tar.gz \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|     情况     |   说明   |
|------------|--------|-------------------------|
| `helm 已安装` | 跳过，不报错 |
| `安装包不存在`   | 文件名不匹配 | 确认 `-x` / `-a` 与实际文件名一致 |

## 相关命令

- [`init helmify`](./helmify.md) — 安装 Helmify（Helm Chart 生成工具）
- [DAG 步骤表](../../../reference/init-all-dag.md)

