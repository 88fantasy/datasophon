# datasophon-cli create registry

## 用途

在指定节点上安装 Sonatype Nexus 制品库，支持两种模式：

- **配置文件模式**（`-c` 指定配置文件）：从 `cluster-sample.yml` 的 `global.registry` 读取参数，SSH 到 registry 节点远程执行，安装成功后自动将 `global.registry.enable` 回写为 `true`。
- **手动模式**（不指定 `-c`）：所有参数通过命令行传入，在**本地节点**执行。适合 registry 节点即当前执行机器，或 Nexus 已独立部署的场景。

## 用法 (Synopsis)

```bash
# 配置文件模式
datasophon-cli [--dry-run] create registry \
  -c <config.yml> --datasophonPath <path> --installPath <path>

# 手动模式
datasophon-cli [--dry-run] create registry \
  --installPath <path> --node <hostname-or-ip> \
  -f <nexus-package-path> --webPort <port> \
  -u <user> -p <password> --dockerHttpPort <port> \
  -r yum -r raw -r docker
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--config` | `-c` | string | `""` | 否 | 配置文件路径；指定后进入配置文件模式 |
| `--datasophonPath` | 无 | string | `""` | 配置文件模式必填 | datasophon 根目录（推导安装包路径） |
| `--installPath` | 无 | string | `""` | 是（两种模式均需）| Nexus 安装路径 |
| `--type` | 无 | string | `"nexus"` | 否 | 制品库类型（目前仅支持 `nexus`） |
| `--node` | 无 | string | `""` | 手动模式必填 | registry 节点 hostname 或 IP |
| `--file` | `-f` | string | `""` | 手动模式必填 | Nexus 安装包完整路径 |
| `--webPort` | 无 | string | `""` | 手动模式必填 | Nexus Web UI 端口 |
| `--user` | `-u` | string | `""` | 手动模式必填 | Nexus 管理员用户名 |
| `--password` | `-p` | string | `""` | 手动模式必填 | Nexus 管理员密码 |
| `--dockerHttpPort` | 无 | int | `0` | 手动模式必填 | Docker 仓库 HTTP 端口 |
| `--repositories` | `-r` | []string | `nil` | 手动模式必填 | 要创建的仓库列表（可多次指定） |

> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)

## 配置文件依赖（配置文件模式）

| 字段 | 说明 |
|---|---|
| `global.registry.node` | registry 节点 hostname（须在 `nodes` 列表中） |
| `global.registry.config.webPort` | Nexus Web 端口 |
| `global.registry.config.user` / `password` | Nexus 凭据 |
| `global.registry.config.dockerHttpPort` | Docker 仓库端口 |
| `global.registry.config.repositories` | 仓库类型列表 |
| `global.packages.nexus.x86_64` / `aarch64` | Nexus 安装包文件名 |
| `global.sshAuthType` | SSH 鉴权方式 |

## 示例

### 配置文件模式 dry-run

```bash
datasophon-cli --dry-run create registry \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  --datasophonPath /data/datasophon \
  --installPath /opt/nexus
```

### 配置文件模式实际执行

```bash
datasophon-cli create registry \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  --datasophonPath /data/datasophon \
  --installPath /opt/nexus
# 安装成功后自动将 global.registry.enable 回写为 true
```

### 手动模式（本地执行）

```bash
datasophon-cli create registry \
  --installPath /opt/nexus \
  --node 192.168.1.10 \
  -f /data/packages/nexus-3.85.0-03-linux-x86_64.tar.gz \
  --webPort 8091 \
  -u admin \
  -p 'YourPassword' \
  --dockerHttpPort 8083 \
  -r yum -r raw -r apt -r docker -r helm
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `配置中未找到 registry 节点: <hostname>` | `global.registry.node` 不在 `nodes` 列表中 | 检查 hostname 拼写；确保 `nodes` 有对应条目 |
| `配置文件模式下 --datasophonPath 为必填项` | 指定了 `-c` 但未给 `--datasophonPath` | 补全 `--datasophonPath` |
| `安装成功但写回配置文件失败` | 文件权限问题 | 检查配置文件的写权限 |

## 相关命令

- [`init registry`](../init/repo/registry.md) — 同一底层逻辑，`create registry` 是其高层封装
- [`upload registry`](../upload/registry.md) — 安装 Nexus 后上传安装包
- [`create cluster`](./cluster.md) — 集群初始化（含 Nexus 步骤）
