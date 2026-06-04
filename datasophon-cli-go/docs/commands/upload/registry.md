# datasophon-cli upload registry

## 用途

将本地 `productPackagesPath` 目录下的安装包批量上传到 Nexus 制品库。按目录结构约定区分仓库类型，并支持 Docker 镜像的 load + push。

此命令从原 `init registryUpload` 迁移而来，现为独立的 `upload` 命令组。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] upload registry \
  --productPackagesPath <dir> \
  --webHost <host> \
  --webPort <port> \
  -u <username> \
  -p <password> \
  --dockerHttpPort <port>
  [--isSuccessDelete]
  [--disableUploadRegistry]
```

## 参数 / Flags

|           flag            |  简写  |   类型   |   默认    | 必填 |             说明              |
|---------------------------|------|--------|---------|----|-----------------------------|
| `--productPackagesPath`   | 无    | string | —       | 是  | 本地安装包根目录（须存在）               |
| `--webHost`               | 无    | string | —       | 是  | Nexus 主机 IP 或 hostname      |
| `--webPort`               | 无    | string | —       | 是  | Nexus Web UI 端口（如 `8091`）   |
| `--username`              | `-u` | string | —       | 是  | Nexus 管理员用户名                |
| `--password`              | `-p` | string | —       | 是  | Nexus 管理员密码                 |
| `--dockerHttpPort`        | 无    | int    | —       | 是  | Docker 仓库 HTTP 端口（如 `8083`） |
| `--isSuccessDelete`       | `-e` | bool   | `false` | 否  | 上传成功后删除本地文件（节省磁盘空间）         |
| `--disableUploadRegistry` | 无    | bool   | `false` | 否  | 设为 `true` 时跳过整个上传流程（调试用）    |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)
>
> **注意**：若继承的 `--enableRegistry` 为 `false`（默认值），上传流程会被跳过。
> 独立调用此命令时，需确保 `--enableRegistry` 为 `true` 或通过 `--config` 传入含 `registry.enable: true` 的配置文件。

## 包目录结构约定

`productPackagesPath` 下的目录结构决定上传的目标仓库：

```
<productPackagesPath>/
├── yum/
│   └── <arch>/           # 如 x86_64 或 aarch64
│       └── <os>/         # 如 centos7、openEuler-22.03-LTS-SP3
│           └── *.rpm     # RPM 包，上传到 Nexus yum 仓库
├── apt/
│   └── <arch>/
│       └── <os>/
│           └── *.deb     # DEB 包，上传到 Nexus apt 仓库
├── raw/
│   └── packages/
│       └── *             # 通用二进制（如 jdk tar、mysql tar），上传到 Nexus raw 仓库
├── helm/
│   └── *.tgz             # Helm Chart，上传到 Nexus helm 仓库
└── docker/
    └── *.tar             # Docker 镜像 tar，通过 `docker load + docker push` 推送到私有仓库
```

> 不符合上述结构的子目录会被跳过并打印 Info 日志。

## Docker 镜像上传流程

`docker/` 目录中的 `.tar` 文件通过以下步骤处理：

1. `docker load -i <file.tar>` — 加载镜像，取镜像 ID
2. `docker tag <imageID> <webHost>:<dockerHttpPort>/docker/<imageName>` — 打标签
3. `docker push <tagged-image>` — 推送到私有仓库

> 执行此命令的节点上须已安装并启动 Docker。

## 配置文件依赖

无强制依赖（可纯 flag 调用）。若配合 `-c` 使用，会读取 `global.registry` 作为上传参数。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run upload registry \
  --productPackagesPath /data/datasophon/datasophon-init/packages \
  --webHost 192.168.1.10 \
  --webPort 8091 \
  -u admin \
  -p 'YourPassword' \
  --dockerHttpPort 8083 \
  --enableRegistry
```

### 实际上传

```bash
datasophon-cli upload registry \
  --productPackagesPath /data/datasophon/datasophon-init/packages \
  --webHost 192.168.1.10 \
  --webPort 8091 \
  -u admin \
  -p 'YourPassword' \
  --dockerHttpPort 8083 \
  --enableRegistry
```

### 上传后删除本地文件

```bash
datasophon-cli upload registry \
  --productPackagesPath /data/datasophon/datasophon-init/packages \
  --webHost 192.168.1.10 \
  --webPort 8091 \
  -u admin -p 'YourPassword' \
  --dockerHttpPort 8083 \
  --enableRegistry \
  --isSuccessDelete
```

## 退出码 / 常见错误

|            错误信息             |                根因                |                    处置                     |
|-----------------------------|----------------------------------|-------------------------------------------|
| `本地安装包目录不存在`                | `--productPackagesPath` 目录不存在    | 确认路径正确且目录已创建                              |
| `enableRegistry=false，跳过上传` | 未设置 `--enableRegistry`（默认 false） | 加 `--enableRegistry` 参数                   |
| 上传失败（status 401）            | Nexus 认证失败                       | 检查 `--username` / `--password`            |
| 上传失败（status 404）            | Nexus 仓库不存在                      | 确认 Nexus 已创建对应仓库（yum/raw/apt/docker/helm） |
| docker push 失败              | Docker 未安装或 Nexus Docker 仓库端口不通  | 确认 Docker 服务正常、防火墙开放 `dockerHttpPort`     |

## 相关命令

- [`create registry`](../create/registry.md) — 先安装 Nexus，再上传包
- [`create cluster`](../create/cluster.md) — 上传完成后执行集群初始化

