# datasophon-cli init ntpslave

## 用途

将目标节点配置为 chrony NTP 从节点，使其从指定的 NTP Server IP 同步时间。需在 NTP Server 节点执行 `create ntp-server` 之后运行。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init ntpslave \
  --ntpServerIp <ip> [公共 flag]
```

## 参数 / Flags

|      flag       | 简写 |   类型   | 默认 | 必填 |        说明        |
|-----------------|----|--------|----|----|------------------|
| `--ntpServerIp` | 无  | string | —  | 是  | NTP Server 节点 IP |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

无（NTP Server IP 通过 `--ntpServerIp` 传入）。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init ntpslave \
  --ntpServerIp 192.168.1.10 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init ntpslave \
  --ntpServerIp 192.168.1.10 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|                   错误信息                   |            根因            |                 处置                 |
|------------------------------------------|--------------------------|------------------------------------|
| `required flag(s) "ntpServerIp" not set` | 未提供 `--ntpServerIp`      | 补上 NTP Server IP                   |
| `chrony 同步失败`                            | NTP Server 端口 123/UDP 不通 | 检查防火墙，确认 `create ntp-server` 已成功执行 |

## 相关命令

- [`create ntp-server`](../../create/ntp-server.md) — 先在 NTP Server 节点执行
- [DAG 步骤表](../../../reference/init-all-dag.md)

