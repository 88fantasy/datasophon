# datasophon-cli init selinux

## 用途

关闭 SELinux（将模式设置为 `disabled`，并修改 `/etc/selinux/config`）。避免 SELinux 策略阻止组件进程的文件访问和网络操作。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init selinux [公共 flag]
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
datasophon-cli --dry-run init selinux \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init selinux \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 情况 | 说明 |
|---|---|
| Ubuntu 节点无 SELinux | 命令会跳过，打印 Info 日志，不报错 |

## 相关命令

- [`init firewall`](./firewall.md) — 同属系统安全关闭步骤
- [DAG 步骤表](../../../reference/init-all-dag.md)
