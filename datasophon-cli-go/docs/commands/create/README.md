# create — 集群创建与组件安装命令组

`create` 命令组提供集群的完整生命周期操作，包括初始化、扩容、配置生成，以及基础组件（Nexus / MySQL / Rustfs / NTP / Nmap / Yum）安装。

> 自 2026-05 重构后，所有"指定节点远程安装"语义的子命令均收敛到 `create` 命令组；它们统一支持**双模式**：
> - **配置文件模式**（`-c`）：从 `cluster-sample.yml` 中读取节点 / 端口 / 凭据，SSH 到目标节点远程执行；安装成功后自动将对应模块的 `enable` 字段写回 `true`。
> - **手动模式**（不带 `-c`）：所有参数通过命令行传入，在**本地节点**执行；不写回配置文件。

## 子命令速查

### 集群与节点

|           命令            |                   说明                   |
|-------------------------|----------------------------------------|
| [cluster](./cluster.md) | 完整集群初始化，走 plan → apply 两阶段流程（33 步 DAG） |
| [node](./node.md)       | 新增单个节点的基础初始化（独立模式，10 步）                |
| [config](./config.md)   | 生成带随机密码的 `cluster-sample.yml` 配置模板     |

### 基础组件安装（双模式）

|               命令                |                    说明                    |          主要写回字段          |
|---------------------------------|------------------------------------------|--------------------------|
| [registry](./registry.md)       | 在 registry 节点安装 Sonatype Nexus 制品库       | `registry.enable=true`   |
| [mysql](./mysql.md)             | 在 mysql 节点安装 MySQL 8（配置文件模式下顺带创建 appDbs） | `mysql.enable=true`      |
| [rustfs](./rustfs.md)           | 在 rustfs 节点安装并启动 Rustfs 对象存储             | `rustfs.enable=true`     |
| [ntp-server](./ntp-server.md)   | 在 ntpServer 节点安装并配置 chrony NTP 服务端       | `ntpServer.enable=true`  |
| [nmap-server](./nmap-server.md) | 在 nmapServer 节点安装 nmap                   | `nmapServer.enable=true` |
| [yum-server](./yum-server.md)   | 在 yumServer 节点配置 httpd/apache2 离线包源      | `yumServer.enable=true`  |

## 典型使用顺序

1. `create config` — 生成配置文件，编辑 `nodes` / `enable` / `node` 字段
2. `create registry` — 安装 Nexus（如启用制品库）
3. `upload registry` — 上传安装包到 Nexus（可选）
4. `create mysql` / `create rustfs` / `create ntp-server` / `create nmap-server` / `create yum-server` — 按需逐项安装基础组件
5. `create cluster` — 执行完整集群初始化（33 步 DAG，自动跳过已 `enable=true` 的步骤所对应的安装动作）
6. `create node` — 后续单节点扩容

> 注：单独运行的 `create <component>` 命令与 `create cluster` 中对应的 DAG 步骤共用底层 Task 实现；适合分阶段排障与重装单个组件的场景。

## 用法

```bash
datasophon-cli [--dry-run] create <subcommand> [flags]
```

全局 `--dry-run` 必须在 `create` 之前：

```bash
datasophon-cli --dry-run create cluster -p /data/datasophon ...
```

## 参考

- [DAG 步骤表](../../reference/init-all-dag.md) — initALL 33 步与各步骤对应的 `create *` 命令
- [配置文件参考](../../config-reference.md) — `cluster-sample.yml` 各字段含义

