# create — 集群创建相关命令组

`create` 命令组提供集群的完整生命周期操作，包括初始化、扩容、配置生成和制品库安装。

## 子命令速查

| 命令 | 说明 |
|---|---|
| [cluster](./cluster.md) | 完整集群初始化，走 plan → apply 两阶段流程 |
| [node](./node.md) | 新增节点初始化（批量模式或独立模式） |
| [config](./config.md) | 生成带随机密码的 `cluster-sample.yml` 配置模板 |
| [registry](./registry.md) | 在指定节点安装 Sonatype Nexus 制品库 |

## 典型使用顺序

1. `create config` — 生成配置文件并手动填写节点 IP、hostname 等
2. `create registry` — 安装 Nexus（如启用制品库）
3. `upload registry` — 上传安装包到 Nexus
4. `create cluster` — 执行完整集群初始化（33 步 DAG）
5. `create node` — 后续扩容新节点

## 用法

```bash
datasophon-cli [--dry-run] create <subcommand> [flags]
```

全局 `--dry-run` 必须在 `create` 之前：

```bash
datasophon-cli --dry-run create cluster -p /data/datasophon ...
```
