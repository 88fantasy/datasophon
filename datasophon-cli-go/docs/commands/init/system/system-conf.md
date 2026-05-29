# datasophon-cli init system-conf

## 用途

优化操作系统性能参数，写入 `/etc/security/limits.conf`（文件描述符限制）、`/etc/sysctl.conf`（内核参数）和 `/etc/rc.local`（开机执行脚本），保证大数据组件运行所需的资源上限。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init system-conf [公共 flag]
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
datasophon-cli --dry-run init system-conf \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init system-conf \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 情况 | 说明 |
|---|---|
| `sysctl -p` 失败 | 内核版本过旧，不支持某参数 | 手动注释 `/etc/sysctl.conf` 中对应行 |

## 相关命令

- [`init osSafeConf`](./ossafeconf.md) — 安全加固（互补）
- [DAG 步骤表](../../../reference/init-all-dag.md)
