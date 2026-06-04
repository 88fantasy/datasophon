# datasophon-cli init allHost

## 用途

读取 `cluster-sample.yml` 中所有节点的 `ip` 和 `hostname`，批量写入目标节点的 `/etc/hosts`，使节点间可通过 hostname 互相访问。需在 `hostname` 配置完成后执行。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init allHost \
  -c <config.yml> [其他公共 flag]
```

## 参数 / Flags

此命令无额外自定义 flag；`-c/--config` 为必填（用于读取节点列表）。

> 继承 init 公共 flag（`-c/--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

|         字段          |         说明          |
|---------------------|---------------------|
| `nodes[*].ip`       | 节点 IP，写入 /etc/hosts |
| `nodes[*].hostname` | 节点 hostname，与 ip 对应 |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init allHost \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init allHost \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|     错误信息     |       根因        |                处置                |
|--------------|-----------------|----------------------------------|
| `nodes 列表为空` | 配置文件中未填写 nodes  | 检查 cluster-sample.yml 的 nodes 列表 |
| 文件权限错误       | /etc/hosts 写入失败 | 确认以 root 用户运行                    |

## 相关命令

- [`init hostname`](./hostname.md) — 先设置单节点主机名
- [DAG 步骤表](../../../reference/init-all-dag.md)

