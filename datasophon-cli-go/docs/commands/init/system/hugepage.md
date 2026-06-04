# datasophon-cli init hugePage

## 用途

关闭内核透明大页（Transparent Huge Pages，THP），并写入 `/etc/rc.local` 使其持久化。THP 会导致 MySQL、Redis、Elasticsearch 等组件出现内存分配延迟抖动。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init hugePage [公共 flag]
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
datasophon-cli --dry-run init hugePage \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init hugePage \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

无已知错误。内核 `/sys/kernel/mm/transparent_hugepage/enabled` 文件不存在时仅打印 Info 日志。

## 相关命令

- [`init system-conf`](./system-conf.md) — 其他系统参数优化
- [DAG 步骤表](../../../reference/init-all-dag.md)

