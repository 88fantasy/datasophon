# Datasophon 离线制品与服务元数据

`package/` 保存 Datasophon 安装服务时使用的制品清单、下载脚本和元数据。这里不是 Maven
构建输出目录：`manifest.json` 定义需要准备的制品，`download.sh` 负责按仓库类型落盘，
`raw/meta/` 则是物理集群和 Kubernetes 服务的声明式元数据源。

## 当前清单

当前 `manifest.json` 共 39 条制品记录，覆盖 26 个服务：

- 35 条公开下载记录；
- 4 条私有制品记录，不配置 `downloadUrl`，需要人工放入目标目录：
  - `datart-server.tar.gz`
  - `gravitino-1.3.1-SNAPSHOT-bin.tar.gz`
  - `valkey-8.1.8-openeuler22.03-x86_64.tar.gz`
  - `valkey-8.1.8-jammy-arm64.tar.gz`

openEuler x86_64 Valkey 私有包的构建输入、脚本和校验值见
[Valkey 构建说明](./docs/valkey/BUILD.md)。

清单字段以脚本实现为准：

| 字段 | 说明 |
|---|---|
| `service` | 服务或制品逻辑名称 |
| `arch` | `x86_64`、`aarch64` 或 `common` |
| `packageName` | 下载后文件名 |
| `decompressPackageName` | 期望的解压目录；不适用时可为 `null` |
| `downloadUrl` | 下载地址；私有制品为 `null` |
| `status` | `public` 或 `private` |
| `repoType` / `repoTypes` | 指定一个或多个目标仓库；未指定时按扩展名推断 |
| `os` | yum/apt 制品的系统目录，例如 `el7`、`el8`、`jammy` |

## 目录路由

`download.sh` 默认把制品写入仓库根目录下的以下位置：

```text
package/
├── manifest.json
├── download.sh
├── verify_decompress.py
├── raw/
│   ├── packages/                 # tar.gz、zip、jar 等通用原始制品
│   └── meta/
│       ├── datacluster-physical/ # 物理集群服务 DDL 与模板
│       └── datacluster-k8s/      # Kubernetes 服务 manifest
├── base/                         # 基础环境制品
├── docker/                       # Docker/OCI 镜像归档
├── helm/                         # Helm Chart
├── yum/<arch>/<os>/              # RPM 仓库布局
└── apt/<arch>/<os>/              # DEB 仓库布局
```

默认推断规则为：`.tar` 进入 `docker/`，`.rpm` 进入 `yum/`，`.deb` 进入
`apt/`，其余进入 `raw/packages/`。显式的 `repoType` 或 `repoTypes` 优先；同一制品
声明多个仓库时，脚本会使用硬链接，失败后退化为复制。

查看脚本计算出的完整布局：

```bash
bash package/download.sh --print-layout
```

## 下载制品

从仓库根目录执行：

```bash
# 下载清单中的全部公开制品（包含所有架构）
bash package/download.sh

# 仅下载指定架构
bash package/download.sh --arch x86_64
bash package/download.sh --arch aarch64

# 仅准备某类仓库
bash package/download.sh --dir raw
bash package/download.sh --dir base
bash package/download.sh --dir docker
bash package/download.sh --dir helm
bash package/download.sh --dir yum
bash package/download.sh --dir apt
```

支持的 `--arch` 值为 `x86_64`、`aarch64`、`common`；选择具体架构时仍会包含
`arch=common` 的通用制品。

支持的 `--dir` 值只有 `raw`、`base`、`docker`、`helm`、`yum`、`apt`。
下载完成后脚本会生成 `<packageName>.md5`。已有 sidecar 时先校验本地 MD5；没有
sidecar 时尝试用远端 `Content-Length` 判断文件是否完整。私有制品会打印待补齐路径，
不会伪造下载成功。

> JDK 清单项当前未显式声明 `repoType`，因此按默认规则下载到
> `raw/packages/`，可供启用制品仓库的 CLI 从 raw 仓库获取。若关闭制品仓库并使用
> `create cluster` 的本地包路径，当前计划会从 `<productPackagesPath>/base` 查找
> JDK，离线执行前需同时把所需 JDK 文件放入该目录。

## 上传到 Nexus

制品准备完成后，可使用 Go CLI 按目录路由上传：

```bash
export DDH_HOME=/opt/datasophon

# 预览，不产生上传；--dry-run 是根命令 flag
datasophon-cli --dry-run upload registry \
  --productPackagesPath ./package \
  --webHost 127.0.0.1 --webPort 8081 \
  -u admin -p 'YourPassword' \
  --dockerHttpPort 8083 \
  --enableRegistry

# 执行上传
datasophon-cli upload registry \
  --productPackagesPath ./package \
  --webHost 127.0.0.1 --webPort 8081 \
  -u admin -p 'YourPassword' \
  --dockerHttpPort 8083 \
  --enableRegistry
```

CLI 上传会识别 `raw/`、`docker/`、`helm/`、`yum/`、`apt/`，并上传
已有的 MD5 sidecar；`base/` 由 CLI 本地直接消费，不上传 Nexus。完整参数和 `--files` 行为见
[CLI 上传命令](../datasophon-cli-go/docs/commands/upload/README.md)。

## 服务元数据

- `raw/meta/datacluster-physical/<SERVICE>/service_ddl.json`：物理集群服务参数、
  角色、依赖、配置写入、Prometheus 与告警定义；模板位于同目录的 `templates/`。
- `raw/meta/datacluster-k8s/<SERVICE>/manifest.yaml`：Kubernetes 服务的
  Chart、命名空间、角色与依赖定义。

Master 启动时从配置的元数据仓库加载这些内容，而不是从 Worker jar 内读取模板。修改元数据
时应保持 DDL/manifest、模板和制品版本一致。

## 解压与版本一致性检查

```bash
python3 package/verify_decompress.py
```

该工具会检查 `manifest.json` 中的压缩包能否解压，并尝试从目录或包名识别实际版本。
它不是只读检查：检测到版本不一致时，可能直接回写 `manifest.json` 以及
`raw/meta/datacluster-physical/**/service_ddl.json`。执行前应确保工作区可审阅，执行后
必须检查 `git diff`；它不校验 Kubernetes manifest。

## 维护清单

新增或升级制品时：

1. 在 `manifest.json` 中维护服务名、架构、文件名、解压目录、下载地址、状态和仓库路由。
2. 运行 `bash package/download.sh --print-layout` 确认目标路径。
3. 下载公开制品，人工补齐私有制品，并核对对应 `.md5`。
4. 如需运行 `verify_decompress.py`，审阅其写回内容，不要直接把自动修改视为正确结果。
5. 同步检查物理集群 DDL、Kubernetes manifest 和模板中的版本引用。
