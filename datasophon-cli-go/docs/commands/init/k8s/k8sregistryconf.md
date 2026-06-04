# datasophon-cli init k8sRegistryConf

## 用途

在目标节点上配置 containerd 私有镜像仓库认证：在 `/etc/containerd/certs.d/<registryIp>:<dockerHttpPort>/` 写入 `hosts.toml`，并向 `/etc/containerd/config.toml` 追加认证信息，然后重启 containerd。使 K8s 节点能从私有 Nexus Docker Registry 拉取镜像。当 `--enableKubernetesCluster` 为 `false` 时跳过。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init k8sRegistryConf \
  --dockerHttpPort <port> \
  [--enableKubernetesCluster] \
  [公共 flag]
```

## 参数 / Flags

|            flag             | 简写 |  类型  |   默认   | 必填 |             说明             |
|-----------------------------|----|------|--------|----|----------------------------|
| `--enableKubernetesCluster` | 无  | bool | `true` | 否  | 为 false 时跳过                |
| `--dockerHttpPort`          | 无  | int  | —      | 是  | 私有 Docker Registry HTTP 端口 |

> 继承 init 公共 flag（`--config`、`--registryIp`、`--registryUsername`、`--registryPassword` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)
>
> **注意**：`--registryIp` / `--registryUsername` / `--registryPassword` 来自公共 flag，用于生成 hosts.toml 和 config.toml 认证配置。

## 配置文件依赖

|                   字段                    |             说明              |
|-----------------------------------------|-----------------------------|
| `global.kubernetes.enable`              | 若为 false，DAG 跳过此步           |
| `global.registry.config.dockerHttpPort` | DAG 自动传入 `--dockerHttpPort` |
| `global.registry.ip`                    | DAG 自动传入 `--registryIp`     |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init k8sRegistryConf \
  --dockerHttpPort 8083 \
  --registryIp 192.168.1.10 \
  --registryUsername admin \
  --registryPassword 'Nexus@123' \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init k8sRegistryConf \
  --dockerHttpPort 8083 \
  --registryIp 192.168.1.10 \
  --registryUsername admin \
  --registryPassword 'Nexus@123' \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|             错误信息             |         根因          |                              处置                              |
|------------------------------|---------------------|--------------------------------------------------------------|
| `containerd certs.d 目录不存在`   | containerd 未安装或版本过旧 | 确认 containerd 已正确安装（由 `init k8sBaseServices` 完成）             |
| `containerd config.toml 不存在` | containerd 配置未初始化   | 执行 `containerd config default > /etc/containerd/config.toml` |

## 相关命令

- [`init k8sBaseServices`](./k8sbaseservices.md) — 部署 K8s 集群（创建 containerd 配置）
- [`init docker`](./docker.md) — 安装 Docker（Docker 模式，与 containerd 区分）
- [DAG 步骤表](../../../reference/init-all-dag.md)

