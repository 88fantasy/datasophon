# datasophon-cli init bash

## 用途

确保 `/bin/sh` 指向 `bash`（而非 `dash`）。部分 Shell 脚本依赖 bash 特性，在 dash 下执行会出错。Ubuntu 默认 `/bin/sh` 为 dash，需要修正。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init bash [公共 flag]
```

## 参数 / Flags

此命令无自定义 flag。

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

无。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init bash \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init bash \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|        情况        |      说明       |
|------------------|---------------|
| CentOS 节点已是 bash | 跳过，打印 Info 日志 |

## 相关命令

- [DAG 步骤表](../../../reference/init-all-dag.md)

