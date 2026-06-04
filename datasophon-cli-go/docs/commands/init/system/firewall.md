# datasophon-cli init firewall

## 用途

关闭节点防火墙服务（firewalld / ufw），防止 RPC、心跳、Nexus 等端口被拦截。集群初始化的最早步骤之一。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init firewall [公共 flag]
```

## 参数 / Flags

此命令无自定义 flag。

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

无（不读取 `cluster-sample.yml` 中的字段）。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init firewall \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init firewall \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

此命令为尽力而为型：即使 firewalld / ufw 不存在也不报错，仅打印 Info 日志。

## 相关命令

- [`init selinux`](./selinux.md) — 通常紧随 firewall 执行
- [DAG 步骤表](../../../reference/init-all-dag.md) — 查看此步在 initALL 中的位置

