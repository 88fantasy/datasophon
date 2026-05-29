# datasophon-cli create rustfs

## 用途

在指定节点安装并启动 Rustfs 对象存储（兼容 S3 协议）。命令解压 tar 包到 `<installPath>/rustfs`、创建 `data/` 与 `logs/` 子目录、生成启动脚本，并以 `--access-key / --secret-key` 启动 rustfs 守护进程。

支持两种模式：

- **配置文件模式**（`-c` 指定配置文件）：从 `cluster-sample.yml` 的 `global.rustfs` 读取参数，**依次**对 `rustfs.nodes` 列表中的每个节点 SSH 远程执行；所有节点安装成功后将 `global.rustfs.enable` 回写为 `true`。
- **手动模式**（不指定 `-c`）：所有参数通过命令行传入，**只对本地节点**执行。

## 用法 (Synopsis)

```bash
# 配置文件模式（多节点）
datasophon-cli [--dry-run] create rustfs \
  -c <config.yml> --datasophonPath <path> --installPath <path>

# 手动模式（本机）
datasophon-cli [--dry-run] create rustfs \
  --installPath <path> --node <bind-host> \
  -f <rustfs-tar> --webPort <port> --apiPort <port> \
  -u <access-key> -p <secret-key>
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--config` | `-c` | string | `""` | 否 | 配置文件路径；指定后进入配置文件模式 |
| `--datasophonPath` | 无 | string | `""` | 配置文件模式必填 | datasophon 根目录（推导安装包路径） |
| `--installPath` | 无 | string | `""` | 是（两种模式均需） | Rustfs 安装根目录（须以 `/` 开头且已存在） |
| `--node` | 无 | string | `""` | 手动模式必填 | Rustfs 节点 hostname 或 IP，同时作为 rustfs 服务绑定地址 |
| `--file` | `-f` | string | `""` | 手动模式必填 | Rustfs tar 安装包完整路径 |
| `--webPort` | 无 | string | `""` | 手动模式必填 | 控制台 Web 端口（如 `9041`） |
| `--apiPort` | 无 | string | `""` | 手动模式必填 | S3 API 端口（如 `9040`） |
| `--user` | `-u` | string | `""` | 手动模式必填 | Access Key |
| `--password` | `-p` | string | `""` | 手动模式必填 | Secret Key |

> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)

## 配置文件依赖（配置文件模式）

| 字段 | 说明 |
|---|---|
| `global.rustfs.nodes` | Rustfs 节点 hostname 列表（至少 1 个，逐个安装） |
| `global.rustfs.config.webPort` | 控制台端口 |
| `global.rustfs.config.apiPort` | S3 API 端口 |
| `global.rustfs.config.user` / `password` | Access Key / Secret Key |
| `global.packages.rustfs.x86_64` / `aarch64` | Rustfs tar 包文件名 |
| `global.sshAuthType` | SSH 鉴权方式 |

> 配置文件模式下，**每个节点的 `WebHost` 自动取节点本身的 IP**（不是 hostname），用于 rustfs `--address` / `--console-address` 绑定。

## 启动命令格式

```
<home>/rustfs --address <ip>:<apiPort> --console-enable --console-address <ip>:<webPort> \
  --access-key <user> --secret-key <password> <home>/data > <home>/logs/rustfs.log 2>&1 &
```

> 命令写到 `<installPath>/rustfs/start.sh` 并以 `bash <home>/start.sh` 启动。

## 示例

### 配置文件模式实际执行

```bash
datasophon-cli create rustfs \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  --datasophonPath /data/datasophon \
  --installPath /opt/rustfs
```

### 手动模式

```bash
datasophon-cli create rustfs \
  --installPath /opt/rustfs \
  --node 192.168.1.10 \
  -f /data/packages/rustfs-linux-x86_64-musl-1.0.0.tar.gz \
  --webPort 9041 \
  --apiPort 9040 \
  -u admin \
  -p 'YourSecretKey'
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `配置中 rustfs.nodes 为空，至少需要一个节点` | 配置文件 `rustfs.nodes` 为空 | 至少填一个 hostname |
| `配置中未找到 rustfs 节点: <hostname>` | `rustfs.nodes` 中的 hostname 不在 `nodes` 列表中 | 检查 hostname 拼写 |
| `rustfs 安装目录不存在` | `--installPath` 指定的目录不存在 | 预先 `mkdir -p` |
| `rustfs 安装包不存在` | tar 包路径错误 | 确认包路径与 `packages.rustfs.*` 文件名一致 |
| `rustfs 启动失败` | 端口被占用 / Access Key 与 Secret Key 太短 | 检查端口；密码长度需满足 rustfs 要求 |
| `节点 <hostname> 安装失败` | 多节点模式中某节点失败 | 该节点失败会终止后续节点；逐节点排障 |

## 相关命令

- [`create cluster`](./cluster.md) — 集群初始化（DAG 步骤 4 `init-rustfs` 复用同一 `rustfsTask`，触发条件 `registry.enable && rustfs.enable`）
