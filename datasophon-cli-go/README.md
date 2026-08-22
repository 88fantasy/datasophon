# datasophon-cli（Go 版）

Datasophon 节点初始化、集群创建和制品上传工具。项目使用 Go 1.21、Cobra、Viper、SSH/SFTP，构建为无需 JDK 的单文件二进制 `datasophon-cli`。

当前主要能力：

- `create cluster`：37 步 plan/apply 集群初始化，支持配置 hash 校验和断点续跑。
- `create node`：12 步新增节点初始化，支持配置文件和手动参数两种模式。
- `create <component>`：远程安装 Nexus、MySQL、Rustfs、NTP、nmap 和 Yum/Apt 离线源。
- `init`：30 个可独立执行的本地初始化命令。
- `upload registry`：按 package 目录布局上传 Nexus raw/yum/apt/helm 制品及 Docker 镜像。

## 快速开始

```bash
cd datasophon-cli-go
make build
sudo cp dist/datasophon-cli /usr/local/bin/

# CLI 入口强制要求 DDH_HOME
export DDH_HOME=/data/datasophon
datasophon-cli --help
```

`DDH_HOME` 为空时程序会直接退出；plan、state 和运行资源都从该目录派生。

## 构建产物

`make release` 在 `dist/` 生成：

| 文件 | 平台 |
|---|---|
| `datasophon-cli-linux-amd64` | Linux x86_64 |
| `datasophon-cli-linux-arm64` | Linux aarch64 |
| `datasophon-cli-darwin-amd64` | macOS Intel |
| `datasophon-cli-darwin-arm64` | macOS Apple Silicon |

二进制名属于外部运维契约，不要重命名。

## 命令概览

```text
datasophon-cli [--dry-run]
├── create
│   ├── cluster [plan|apply]
│   ├── node
│   ├── config
│   ├── registry / mysql / rustfs
│   ├── ntp-server / nmap-server / yum-server
├── init                         # 30 个单步命令
└── upload
    └── registry
```

`--dry-run` 是根命令的全局 flag，应写在子命令之前，例如：

```bash
datasophon-cli --dry-run create cluster plan \
  -t hadoop \
  -p /data/datasophon \
  --installPath /opt/install \
  --productPackagesPath /data/install_datasophon/package
```

## 文档

| 文档 | 说明 |
|---|---|
| [快速开始](./docs/getting-started.md) | 从目录准备到第一次 plan/apply |
| [全局选项](./docs/global-flags.md) | `--dry-run`、SSH 鉴权和公共参数 |
| [配置文件参考](./docs/config-reference.md) | `cluster-sample.yml` 字段 |
| [命令参考](./docs/README.md) | `create`、`init`、`upload` 完整索引 |
| [初始化 DAG](./docs/reference/init-all-dag.md) | 37 步 initALL 与 12 步新增节点流程 |
| [退出码与断点续跑](./docs/reference/exit-codes.md) | 失败恢复和 plan 状态 |

## 开发与验证

```bash
make build              # 当前平台二进制
make release            # 四个平台交叉编译
make test               # go test ./...
make vet                # go vet ./...
make clean              # 清理 dist/

go fmt ./...
go test -cover ./...
```

新增命令或 Step 时需同步单元测试和对应命令文档。根项目关系见 [仓库 README](../README.md)。

## License

沿用仓库根目录的 [LICENSE](../LICENSE)。
