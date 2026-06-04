# datasophon-cli init kuboard

## 用途

使用 sealos 在 Kubernetes 集群中安装 Kuboard（K8s 可视化管理界面）。执行前会对 `--etcds` 指定的节点打 `k8s.kuboard.cn/role=etcd` 标签。当 `--enableKubernetesCluster` 为 `false` 时跳过；若 kuboard pods 已存在也跳过。

访问地址：`http://<master-ip>:30080`，默认账号 `admin`，首次登录需修改密码。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init kuboard \
  -e <etcd1,etcd2,...> \
  --packagePath <dir> \
  [--kuboardX86Tar <filename>] \
  [--kuboardArmTar <filename>] \
  [--enableKubernetesCluster] [--kubernetesForce] \
  [公共 flag]
```

## 参数 / Flags

|            flag             |  简写  |    类型    |   默认    | 必填 |                 说明                 |
|-----------------------------|------|----------|---------|----|------------------------------------|
| `--enableKubernetesCluster` | 无    | bool     | `true`  | 否  | 为 false 时跳过 Kuboard 安装             |
| `--kuboardX86Tar`           | 无    | string   | `""`    | 否  | x86_64 Kuboard sealos 镜像 tar 包文件名  |
| `--kuboardArmTar`           | 无    | string   | `""`    | 否  | aarch64 Kuboard sealos 镜像 tar 包文件名 |
| `--etcds`                   | `-e` | []string | —       | 是  | etcd 节点 hostname 列表（用于打标签，可多次指定）   |
| `--packagePath`             | 无    | string   | —       | 是  | 安装包目录                              |
| `--kubernetesForce`         | 无    | bool     | `false` | 否  | 已存在时是否覆盖安装                         |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

|                      字段                      |        说明         |
|----------------------------------------------|-------------------|
| `global.kubernetes.enable`                   | 若为 false，DAG 跳过此步 |
| `global.kubernetes.etcds`                    | DAG 自动传入 `-e`     |
| `global.packages.kuboard.x86_64` / `aarch64` | 包文件名              |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init kuboard \
  -e master01 \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --kuboardX86Tar kuboard-v3.5.2.2-linux-amd64.tar \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init kuboard \
  -e master01 \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --kuboardX86Tar kuboard-v3.5.2.2-linux-amd64.tar \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|         错误信息          |         根因         |                    处置                     |
|-----------------------|--------------------|-------------------------------------------|
| `etcd 节点打标签失败`        | kubectl 命令失败或节点名不对 | 确认节点 hostname 已注册到集群（`kubectl get nodes`） |
| `安装 kuboard 失败`       | sealos run 失败      | 检查 sealos 版本与 kuboard 包兼容性                |
| `kuboard pods 已存在，跳过` | 重复执行               | 正常，不报错                                    |

## 相关命令

- [`init k8sBaseServices`](./k8sbaseservices.md) — 先部署 K8s 集群
- [DAG 步骤表](../../../reference/init-all-dag.md)

