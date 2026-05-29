# 全局选项与公共参数

## --dry-run

所有命令均支持 `--dry-run` 全局 flag，必须在子命令**之前**指定。

```bash
datasophon-cli --dry-run <command> [subcommand] [flags]
```

| 效果 | 说明 |
|---|---|
| 打印命令 | 将要执行的 shell 命令逐条打印到标准输出 |
| 不执行 | 不在任何节点实际运行命令，也不修改任何文件 |
| 网络连接 | SSH 连接**仍会建立**（用于探测 OS 类型/架构） |

**示例**：

```bash
# 预检 create cluster 的全部 33 步
datasophon-cli --dry-run create cluster \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages

# 预检单步 init
datasophon-cli --dry-run init firewall \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml
```

> `--dry-run` 是开始任何生产变更前的**强烈推荐**步骤。

---

## SSH 鉴权

SSH 鉴权类型通过 `cluster-sample.yml` 中的 `global.sshAuthType` 字段控制，
也可以在 `create node` 时通过 `--user`/`--password` 参数单独指定。

| 值 | 行为 |
|---|---|
| `PASSWORD` | 仅使用密码认证（`nodes[*].password` 字段） |
| `PUBLICKEY` | 仅使用公钥认证（依赖 `~/.ssh/id_rsa` 等默认密钥） |
| `AUTO` | 先尝试公钥，失败后回退到密码认证 |

**推荐**：使用 `AUTO`，在混合认证环境（部分节点已配置免密、部分未配置）下最为稳健。

**示例**（`cluster-sample.yml`）：

```yaml
global:
  sshAuthType: "AUTO"     # 推荐；兼容新旧节点
```

> 运行 [`init ssh`](./commands/init/network/ssh.md) 可将本机公钥分发到所有节点，之后可将 `sshAuthType` 改为 `PUBLICKEY` 并清空 `nodes[*].password`。

---

## init 公共参数 {#init-公共-flag}

所有 `init` 子命令都继承以下 6 个公共 flag（由 `TaskBase.AddBaseFlags` 注入）。这些 flag 是可选的，仅当命令需要读取集群配置或访问制品库时才有意义。

| flag | 简写 | 类型 | 默认 | 说明 |
|---|---|---|---|---|
| `--config` | `-c` | string | `""` | 集群配置文件路径（`cluster-sample.yml`） |
| `--registryIp` | 无 | string | `""` | 制品库 IP（覆盖配置文件中的 `global.registry.config.*`） |
| `--registryPort` | 无 | string | `""` | 制品库端口 |
| `--registryUsername` | 无 | string | `""` | 制品库用户名 |
| `--registryPassword` | 无 | string | `""` | 制品库密码 |
| `--enableRegistry` | 无 | bool | `false` | 是否从制品库拉取安装包（`true` 时忽略本地包路径） |

**典型用法**：

```bash
# 使用配置文件（常见于分布式操作多台节点的命令）
datasophon-cli init firewall \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml

# 使用制品库（离线环境，包从 Nexus 拉取）
datasophon-cli init jdk17 \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  --enableRegistry \
  --registryIp 192.168.1.10 \
  --registryPort 8091 \
  --registryUsername admin \
  --registryPassword yourpass
```

> 大多数 init 子命令也有自己的专用 flag，详见各命令页。
> init 公共 flag 优先级低于子命令自有 flag；命令行传入的值会覆盖配置文件中的对应字段。
