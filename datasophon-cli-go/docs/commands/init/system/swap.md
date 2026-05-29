# datasophon-cli init swap

## 用途

关闭节点 Swap 分区（`swapoff -a`），并注释 `/etc/fstab` 中的 swap 行，确保重启后仍不挂载。Kubernetes 和大数据组件要求 Swap 关闭。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init swap [公共 flag]
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
datasophon-cli --dry-run init swap \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init swap \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

无已知错误。节点若未配置 Swap，命令正常退出。

## 相关命令

- [DAG 步骤表](../../../reference/init-all-dag.md)
