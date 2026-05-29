# datasophon-cli（Go 版）

**Go 1.21 | Cobra | 零依赖静态二进制 | 跨平台**

Datasophon 集群节点初始化和管理工具，用 Go 重写，无需 JDK，单文件即可运行。

## 快速开始

```bash
# 构建（需 Go 1.21+）
make build

# 安装到 PATH
sudo cp dist/datasophon-cli /usr/local/bin/

# 验证
datasophon-cli --help
```

交叉编译产物位于 `dist/`：

| 文件 | 平台 |
|---|---|
| `datasophon-cli-linux-amd64` | Linux x86_64（生产主流） |
| `datasophon-cli-linux-arm64` | Linux aarch64 |
| `datasophon-cli-darwin-amd64` | macOS Intel |
| `datasophon-cli-darwin-arm64` | macOS Apple Silicon |

## 文档

| 文档 | 说明 |
|---|---|
| **[快速开始](./docs/getting-started.md)** | 从零到跑通第一次 `create cluster` |
| [全局选项](./docs/global-flags.md) | `--dry-run`、SSH 鉴权、init 公共参数 |
| [配置文件参考](./docs/config-reference.md) | `cluster-sample.yml` 完整字段表 |
| [命令参考（全）](./docs/README.md) | 所有命令的完整索引 |

## 构建命令

```bash
make build      # 当前平台二进制（输出到 dist/datasophon-cli）
make release    # 交叉编译 4 个目标
make test       # 运行测试
make vet        # 代码检查
make clean      # 清理产物
```

## 贡献指南

```bash
go fmt ./...           # 格式化
go vet ./...           # 静态检查
go test -cover ./...   # 测试 + 覆盖率
```

提交要求：新功能附带单元测试；`make test && make vet` 通过；遵循 Go 命名约定。

## 许可证

MIT License — 2024 Datasophon Contributors
