# datasophon-cli (Go 版)

> **Phase 1** — 骨架 + 10 个核心 init 任务 + `create cluster initSingleNode` 子集

## 为什么重写

| 项目 | Java 版 | Go 版 |
|---|---|---|
| 运行时依赖 | JDK 17（≈200MB） | 无（静态二进制） |
| 单文件大小 | ~30MB fat-jar | ~10-15MB |
| 跨平台发布 | 需要 JRE/JDK | 单文件，`GOOS/GOARCH` 交叉编译 |
| 离线环境 | CLI 本身需要先装 JDK，但 CLI 负责装 JDK | 无依赖循环 |

## 快速开始

### 构建

```bash
# 需要 Go 1.21+
make build           # 本机平台 → dist/datasophon-cli
make release         # 4 个目标平台 → dist/datasophon-cli-{linux,darwin}-{amd64,arm64}
```

### 运行要求

```bash
export DDH_HOME=/opt/datasophon   # 必填，与 Java 版保持一致
```

### 命令树

```
datasophon-cli [--dry-run]
├── create
│   └── cluster  -p <path> --installPath <path> --cpassword <pwd> -a initSingleNode --productPackagesPath <pkgs>
└── init
    ├── firewall      关闭防火墙（firewalld / ufw）
    ├── selinux       关闭 SELinux
    ├── swap          关闭 Swap 分区
    ├── hostname  -H  设置主机名
    ├── allHost       更新 /etc/hosts
    ├── osUser        创建 hadoop 用户和组
    ├── library       安装运行时依赖库（yum/apt 自动选择）
    ├── bash          确保 /bin/sh → bash
    ├── jdk17     -p  安装 OpenJDK 17（从本地 tar 包）
    └── ssh       -c  配置 SSH 免密登录（本地执行，向所有节点分发公钥）
```

### 示例

```bash
# 单节点初始化（dry-run 模式，仅打印命令）
DDH_HOME=/opt/ddh ./datasophon-cli create cluster \
    -p /opt/ddh \
    --installPath /opt/install \
    --cpassword '' \
    -a initSingleNode \
    --productPackagesPath /opt/packages \
    --dry-run

# 单独初始化防火墙（直连远程节点）
DDH_HOME=/opt/ddh ./datasophon-cli init firewall \
    -c /opt/ddh/datasophon-init/config/cluster-sample.yml

# 配置 SSH 免密
DDH_HOME=/opt/ddh ./datasophon-cli init ssh \
    -c /opt/ddh/datasophon-init/config/cluster-sample.yml
```

## 项目结构

```
datasophon-cli-go/
├── cmd/datasophon-cli/main.go        # 入口：DDH_HOME 检查 + Execute()
├── internal/
│   ├── cli/
│   │   ├── root.go                   # Cobra 根命令，注册 create/init
│   │   ├── create/cluster.go         # create cluster（initSingleNode 子集）
│   │   └── init/*.go                 # 10 个 init 子命令
│   ├── executor/                     # Executor 接口 + Local/SSH/Batch 实现
│   ├── handler/                      # Handler 接口 + Chain（复用 ssh.Client）
│   ├── config/                       # ClusterConfig YAML 解析
│   └── osinfo/                       # OS/Arch 类型探测
├── test/
│   ├── fixtures/cluster-sample.yml   # 明文密码测试固件
│   ├── config/loader_test.go
│   └── executor/local_test.go
└── Makefile
```

## 与 Java 版的差异

| 特性 | Java 版 | Go 版（Phase 1） |
|---|---|---|
| jasypt 加密密码 | 支持 PBEWithMD5AndDES | ⚠️ 仅明文，Phase 4 补齐 |
| `initALL` 完整 DAG | 28 步 | ⚠️ TODO Phase 3 |
| 剩余 15 个 init 任务 | 全部 | ⚠️ TODO Phase 2 |
| Registry SPI | 有接口（死代码） | 已删除（无实现） |
| `bin/sbin` 脚本 | 有 | 已删除（只暴露二进制） |

## 路线图

| Phase | 内容 | 状态 |
|---|---|---|
| **Phase 1** | 骨架 + 10 init 任务 + `initSingleNode` | ✅ 完成 |
| Phase 2 | 补齐剩余 15 个 init 任务 | ⬜ Pending |
| Phase 3 | `initALL` 完整 28 步 DAG | ⬜ Pending |
| Phase 4 | jasypt PBEWithMD5AndDES 兼容 | ⬜ Pending |
| Phase 5 | 文档迁移 | ⬜ Pending |
| Phase 6 | 删除 Java `datasophon-cli` 模块 | ⬜ Pending |

## 开发

```bash
go test ./...        # 单元测试
go vet ./...         # 静态检查
make build           # 本机编译
```
