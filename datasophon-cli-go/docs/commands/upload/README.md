# upload — 制品包上传命令组

`upload` 命令组负责将本地组件安装包上传到 Nexus 制品库。

## 子命令速查

| 命令 | 说明 |
|---|---|
| [registry](./registry.md) | 批量将本地安装包目录上传到 Nexus |

## 典型使用场景

在 `create registry`（Nexus 安装）完成之后、`create cluster`（集群初始化）开始之前，
将 `packages/` 目录下的安装包上传到 Nexus，以便后续节点通过制品库拉取包。

## 用法

```bash
datasophon-cli [--dry-run] upload <subcommand> [flags]
```
