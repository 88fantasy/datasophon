# datasophon-cli init tar

## 用途

确认目标节点上 `tar` 命令可用（执行 `tar --version`）。若指定了 `--packagePath`，则尝试从该路径下载 tar 安装包并安装（适用于极简系统镜像）。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init tar \
  [-p <packagePath>] [公共 flag]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--packagePath` | `-p` | string | `""` | 否 | 安装包目录（不填则仅检测 tar 是否存在） |

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

### 指定包路径（含安装兜底）

```bash
datasophon-cli init tar \
  -p /data/packages \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 情况 | 说明 |
|---|---|
| tar 已存在 | 直接返回成功，不做任何操作 |
| tar 不存在且未指定 `--packagePath` | 报错退出 |

## 相关命令

- [DAG 步骤表](../../../reference/init-all-dag.md)
