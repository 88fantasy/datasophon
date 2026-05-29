# datasophon-cli init ssh

## 用途

在**当前执行机器**上生成 SSH 密钥对（若未存在），然后将公钥分发到 `cluster-sample.yml` 中所有节点，实现免密登录。此命令直接在本地执行，不走 SSH 远程，因此不继承 `TaskBase` 公共 flag（如 `--registryIp`）。

> **注意**：命令名为 `ssh`，不是 `ssh_free`（旧版 README 错误写法）。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init ssh \
  -c <config.yml>
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--config` | `-c` | string | — | 是 | 集群配置文件路径，用于读取节点列表 |

> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)
> **不继承** init 公共 flag（TaskBase），因为此命令在本地执行。

## 配置文件依赖

| 字段 | 说明 |
|---|---|
| `nodes[*].ip` | 目标节点 IP |
| `nodes[*].port` | SSH 端口 |
| `nodes[*].user` | SSH 用户 |
| `nodes[*].password` | SSH 密码（用于首次 ssh-copy-id） |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init ssh \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init ssh \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `required flag(s) "config" not set` | 未传入 `-c` | 补上 `-c cluster-sample.yml` |
| `ssh-copy-id 失败` | 节点密码错误或 SSH 端口不通 | 检查 nodes[*].password 和 port |

## 相关命令

- [`init allHost`](./allhost.md) — 通常在 ssh 免密配置后执行
- [DAG 步骤表](../../../reference/init-all-dag.md)
