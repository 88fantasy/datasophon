# datasophon-cli 运维使用手册

datasophon-cli 是 Datasophon 集群管理平台的命令行工具（Go 重写版，零依赖静态二进制），用于节点初始化、集群创建与 Nexus 制品库维护。

## 文档导航

| 文档 | 说明 |
|---|---|
| [快速开始](./getting-started.md) | 从零安装到跑通第一次 `create cluster` |
| [全局选项](./global-flags.md) | `--dry-run`、SSH 鉴权、init 公共参数 |
| [配置文件参考](./config-reference.md) | `cluster-sample.yml` 完整字段表 |
| [命令参考 → create](./commands/create/README.md) | 集群创建、节点扩容、配置生成、Nexus 安装 |
| [命令参考 → init](./commands/init/README.md) | 31 条单步初始化子命令 |
| [命令参考 → upload](./commands/upload/README.md) | 制品包批量上传 |
| [DAG 步骤表](./reference/init-all-dag.md) | initALL 33 步 / initSingleNode 17 步 / standalone 10 步 |
| [退出码与断点续跑](./reference/exit-codes.md) | 错误处置与恢复方法 |

## 命令树速查

```
datasophon-cli [--dry-run]
├── create                          # 集群创建相关命令组
│   ├── cluster                     # 完整集群初始化（plan → apply）
│   │   ├── plan                    # 仅生成计划到 state/initALL.plan.json
│   │   └── apply                   # 读取计划并执行（支持断点续跑）
│   ├── node                        # 新增节点初始化
│   ├── config                      # 生成 cluster-sample.yml 配置模板
│   └── registry                    # 在指定节点安装 Nexus 制品库
├── init                            # 单步初始化（31 条子命令）
│   ├── system/   firewall  selinux  swap  library  osSafeConf  system-conf  osUser  bash  hugePage
│   ├── network/  hostname  allHost  nmap  ntpserver  ntpslave  ssh
│   ├── packages/ bin_packages  tar  jdk8  jdk17
│   ├── repo/     offlineServer  offlineSlave  registry  registryDecode  rustfs
│   ├── db/       mysql  mysql_app_db
│   └── k8s/      docker  helm  helmify  kubectl  k8sBaseServices  k8sRegistryConf  kuboard
└── upload                          # 制品包上传
    └── registry                    # 批量上传安装包到 Nexus
```

## 按任务快速定位

| 运维场景 | 推荐入口 |
|---|---|
| 首次搭建集群 | [快速开始](./getting-started.md) → `create cluster` |
| 扩容新节点 | [`create node`](./commands/create/node.md) |
| 生成/更新配置文件 | [`create config`](./commands/create/config.md) |
| 安装 Nexus 制品库 | [`create registry`](./commands/create/registry.md) 或 [`init registry`](./commands/init/repo/registry.md) |
| 上传安装包到 Nexus | [`upload registry`](./commands/upload/registry.md) |
| 单步操作某节点 | [`init <subcommand>`](./commands/init/README.md) |
| 查看初始化步骤顺序 | [DAG 步骤表](./reference/init-all-dag.md) |
| 执行失败后如何续跑 | [退出码与断点续跑](./reference/exit-codes.md) |
| 了解 SSH 鉴权配置 | [全局选项 → SSH 鉴权](./global-flags.md#ssh-鉴权) |
