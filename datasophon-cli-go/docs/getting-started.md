# 快速开始

本文档引导运维人员从零完成 datasophon-cli 的安装和第一次集群初始化。

## 前置条件

|   条件    |                    说明                    |
|---------|------------------------------------------|
| 操作系统    | Linux x86_64 或 aarch64（macOS 仅用于开发）      |
| 执行权限    | root 或具有 sudo 权限的用户                      |
| SSH 连通性 | 本机到所有集群节点可 SSH 登录                        |
| 安装包就绪   | 组件安装包已下载到本机（路径即 `--productPackagesPath`） |

## 第一步：获取二进制

### 方式 A — 直接下载预编译产物

从 Datasophon 发布包中取出适合当前平台的二进制：

```bash
# Linux x86_64
cp dist/datasophon-cli-linux-amd64 /usr/local/bin/datasophon-cli
chmod +x /usr/local/bin/datasophon-cli

# Linux aarch64
cp dist/datasophon-cli-linux-arm64 /usr/local/bin/datasophon-cli
chmod +x /usr/local/bin/datasophon-cli
```

### 方式 B — 从源码构建（需要 Go 1.21+）

```bash
cd datasophon-cli-go
make build                          # 输出到 dist/datasophon-cli
sudo cp dist/datasophon-cli /usr/local/bin/
```

### 验证

```bash
datasophon-cli --help
# 应输出：
# Usage:
#   datasophon-cli [flags]
#   datasophon-cli [command]
```

## 第二步：准备目录结构

datasophon-cli 依赖固定的目录布局。以 `DATASOPHON_PATH=/data/datasophon` 为例：

```
/data/datasophon/
└── datasophon-init/
    ├── config/
    │   └── cluster-sample.yml      # 集群配置文件（第三步生成）
    └── packages/                   # 组件安装包目录
        ├── openEuler-22.03-LTS-SP3.tar.gz
        ├── nexus-3.85.0-03-linux-x86_64.tar.gz
        ├── mysql-8.0.28-1.el8.x86_64.rpm-bundle.tar
        └── ...
```

创建目录：

```bash
mkdir -p /data/datasophon/datasophon-init/{config,packages}
```

将安装包放入 `packages/` 目录，包名必须与配置文件中 `global.packages.*` 字段的值一致。

## 第三步：生成配置文件

使用 `create config` 生成带随机密码的配置模板：

```bash
datasophon-cli create config \
  --output /data/datasophon/datasophon-init/config/cluster-sample.yml
```

> `create config` 会自动填充：
> - `RegistryPassword` / `MySQLRootPassword` / `RustfsPassword` — 随机安全密码
> - `CurrentNodeIP` — 当前执行机器的本机 IP（作为 `nodes[0].ip` 初始值）

生成后**必须手动编辑**以下字段（详见 [配置文件参考](./config-reference.md)）：

```yaml
global:
  registry:
    node: "app6"          # 运行 Nexus 的节点 hostname
    enable: true          # 是否启用制品库
  mysql:
    enable: true
    node: "app6"          # 运行 MySQL 的节点 hostname
  ntpServer:
    enable: true
    node: "app6"          # NTP 服务端节点
  kubernetes:
    enable: true          # 是否部署 K8s（不需要则设为 false）

nodes:                    # 集群所有节点（至少 1 个）
  - ip: 192.168.1.10
    user: root
    password: "yourpass"
    port: 22
    hostname: app6
```

## 第四步：dry-run 预检

在实际执行前，用 `--dry-run` 验证计划是否符合预期：

```bash
datasophon-cli create cluster --dry-run \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
```

`--dry-run` 会打印每一步将要执行的 shell 命令，**不实际在任何节点上执行**。

## 第五步：生成并确认计划

```bash
datasophon-cli create cluster plan \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
```

计划文件保存到：`/data/datasophon/datasophon-init/state/initALL.plan.json`

输出示例：

```
[Plan] initALL  步骤总数: 33  待执行: 33  已完成: 0
  [  1] init-bin-package         ✔ 待执行   (targets: app6)
  [  2] init-bash                ✔ 待执行   (targets: app6)
  ...
```

## 第六步：执行初始化

**方式 A — 一键 plan + 确认 + apply（推荐新手）**

```bash
datasophon-cli create cluster \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
# 输出计划摘要后提示：确认执行以上计划? [y/N]
# 输入 y 回车即开始执行
```

**方式 B — 跳过确认直接执行**

```bash
datasophon-cli create cluster -y \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
```

**方式 C — plan 和 apply 分开执行（推荐生产）**

```bash
# 先 plan（可在任意时间执行 apply）
datasophon-cli create cluster plan \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages

# 审阅计划后，apply（支持断点续跑）
datasophon-cli create cluster apply \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
```

> 如果 apply 中途失败，再次执行 apply 会从上次失败的步骤重新开始，已成功的步骤不会重复执行。详见 [退出码与断点续跑](./reference/exit-codes.md)。

## 常见问题

**问：执行时报 "节点 IP 不存在于 nodes 列表"**
答：配置文件 `nodes[*].ip` 没有一个匹配当前执行机器的本机 IP。请检查 `cluster-sample.yml` 中至少有一个节点的 `ip` 字段等于本机 IP。

**问：如何只初始化部分功能（不装 K8s）？**
答：在 `cluster-sample.yml` 中把对应模块的 `enable` 设为 `false`（如 `global.kubernetes.enable: false`），DAG 中相关步骤会自动跳过。

**问：如何新增节点而不重跑全量初始化？**
答：用 `create node --ip <IP> --user <user> --password <pass> --port <port> --hostname <hn>`，对单节点执行 10 步基础初始化。详见 [`create node`](./commands/create/node.md)。安装成功后该节点会自动追加到 `cluster-sample.yml` 的 `nodes` 列表。

**问：MySQL / Nexus / Rustfs / NTP / nmap 安装为什么不在 init 命令组里？**
答：自 2026-05 重构后，这些涉及"特定节点远程安装"的子命令已统一搬到 `create` 命令组下，并新增了双模式（配置文件模式 + 手动模式）。`init` 命令组现在专门承载"在已登录节点上单步本地初始化"语义。完整命令对照见 [`create` 命令参考](./commands/create/README.md)。
