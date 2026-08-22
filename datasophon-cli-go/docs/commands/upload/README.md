# upload — 制品上传命令组

`upload` 负责把本地 `package/` 布局中的服务包、元数据、Helm Chart 和 Docker 镜像上传到 Nexus。当前只有 `registry` 子命令。

## 子命令

| 命令 | 说明 |
|---|---|
| [registry](./registry.md) | 扫描目录批量上传，或通过 `--files` 精确上传指定文件 |

## 目录路由

`--productPackagesPath` 应指向 package 根目录，首级目录决定目标仓库：

```text
<productPackagesPath>/
├── yum/<arch>/<os>/        # Nexus yum
├── apt/<arch>/<os>/        # Nexus apt
├── raw/packages/           # Nexus raw /packages
├── raw/meta/               # Nexus raw /meta
├── helm/                   # Nexus helm
└── docker/                 # docker load + tag + push
```

`base/` 是 CLI 本地直接消费的基础设施制品，不属于整目录 Nexus 上传范围。

## 典型流程

```bash
# 1. 安装并启用 Nexus
datasophon-cli create registry ...

# 2. 预检整目录上传；全局 --dry-run 必须放在 upload 之前
datasophon-cli --dry-run upload registry \
  --productPackagesPath /data/install_datasophon/package \
  --webHost 127.0.0.1 --webPort 8081 \
  -u admin -p 'YourPassword' \
  --dockerHttpPort 8083 \
  --enableRegistry

# 3. 实际上传
datasophon-cli upload registry \
  --productPackagesPath /data/install_datasophon/package \
  --webHost 127.0.0.1 --webPort 8081 \
  -u admin -p 'YourPassword' \
  --dockerHttpPort 8083 \
  --enableRegistry
```

`--enableRegistry` 默认是 `false`；独立调用时不显式开启会直接跳过上传。

## 精确文件模式

只变更少量元数据或安装包时，可重复传入 `--files`，路径必须相对 `productPackagesPath`：

```bash
datasophon-cli upload registry \
  --productPackagesPath /data/install_datasophon/package \
  --webHost 127.0.0.1 --webPort 8081 \
  -u admin -p 'YourPassword' \
  --dockerHttpPort 8083 \
  --enableRegistry \
  --files raw/meta/datacluster-physical/DORIS/service_ddl.json \
  --files raw/packages/apache-doris-4.1.3-bin-x64.tar.gz
```

该模式具有以下差异：

- 不扫描整个目录，也不处理 `docker/` 镜像。
- 指定文件强制覆盖上传，不执行整目录模式的远端 MD5 相同跳过逻辑。
- `raw/packages/` 下需要校验的主包会刷新本地 `.md5` sidecar，并要求主包和 sidecar 都上传成功。
- 拒绝绝对路径、`..` 越界和解析后逃出 package 根目录的符号链接。

完整 flags、目录规则和错误处理见 [`upload registry`](./registry.md)。
