# datasophon-cli init tar

## 用途

确认目标节点上 `tar` 命令可用。若 tar 缺失且指定了 `--productPackagesPath`，从 CLI 本机的
`<productPackagesPath>/yum/<arch>/<os>/` 找到 tar 的 rpm/deb 包，分发到目标节点后用
`rpm -ivh` 或 `dpkg -i` 直接安装（适用于离线极简系统镜像）。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init tar \
  [-p <packagePath>] [--productPackagesPath <path>] [公共 flag]
```

## 参数 / Flags

|      flag       |  简写  |   类型   |  默认  | 必填 |           说明           |
|-----------------|------|--------|------|----|------------------------|
| `--packagePath` | `-p` | string | `""` | 否  | 兼容保留参数，当前不参与 tar 安装 |
| `--productPackagesPath` | — | string | `""` | 否 | 离线源根目录；tar 缺失时从 `yum/<arch>/<os>/` 安装本地 rpm/deb 包 |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

无。

## 示例

### 仅检测 tar 是否可用

```bash
datasophon-cli --dry-run init tar \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 指定离线源根目录（含安装兜底）

```bash
datasophon-cli init tar \
  --productPackagesPath /data/install_datasophon/package \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|             情况              |      说明       |
|-----------------------------|---------------|
| tar 已存在                     | 直接返回成功，不做任何操作 |
| tar 不存在且未指定 `--productPackagesPath` | 报错退出          |
| tar 不存在且离线源中无安装包 | 报错并提示应放置 rpm/deb 包的目录 |

## 相关命令

- [DAG 步骤表](../../../reference/init-all-dag.md)
