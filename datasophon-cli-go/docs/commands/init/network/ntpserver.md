# datasophon-cli init ntpserver

## 用途

在目标节点上安装并配置 `chrony` NTP 服务端，使其作为集群内部时间源。其他节点通过 `init ntpslave` 同步到此节点。集群时间同步是 Kerberos、HDFS NameNode HA 等服务的前置要求。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init ntpserver [公共 flag]
```

## 参数 / Flags

此命令无自定义 flag。

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

| 字段 | 说明 |
|---|---|
| `global.ntp.ntpServerNode` | 用于标识 NTP Server 节点（在 DAG 中选择目标节点） |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init ntpserver \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init ntpserver \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `chrony 安装失败` | 无软件源 | 先执行 `init library` / 配置离线源 |
| `chronyd 启动失败` | 端口 123/UDP 被占用 | 检查是否有其他 NTP 服务运行 |

## 相关命令

- [`init ntpslave`](./ntpslave.md) — 配置其他节点同步到此服务端
- [DAG 步骤表](../../../reference/init-all-dag.md)
