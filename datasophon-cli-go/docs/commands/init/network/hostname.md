# datasophon-cli init hostname

## 用途

将目标节点的主机名设置为指定值（通过 `hostnamectl set-hostname`）。主机名是 Datasophon 集群节点唯一标识，必须在所有其他网络配置之前完成。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init hostname \
  -H <hostname> [公共 flag]
```

## 参数 / Flags

|     flag     |  简写  |   类型   | 默认 | 必填 |   说明    |
|--------------|------|--------|----|----|---------|
| `--hostname` | `-H` | string | —  | 是  | 要设置的主机名 |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

无（主机名通过 `-H` 参数传入，不从配置文件读取）。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init hostname \
  -H master01 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init hostname \
  -H master01 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|                 错误信息                  |     根因      |         处置         |
|---------------------------------------|-------------|--------------------|
| `required flag(s) "hostname" not set` | 未提供 `-H` 参数 | 补上 `-H <hostname>` |

## 相关命令

- [`init allHost`](./allhost.md) — 把所有节点 hostname 写入 /etc/hosts
- [DAG 步骤表](../../../reference/init-all-dag.md)

