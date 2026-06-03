# Datasophon 部署包管理

本目录用于管理 Datasophon 各组件的部署包，包含下载清单、批量下载脚本和解压目录校验工具。

## 目录结构

```
package/
├── manifest.json          # 部署包清单（服务名、架构、下载地址）
├── download.sh            # 批量下载脚本
├── verify_decompress.py   # 解压目录校验工具
└── <packageName>.tar.gz   # 下载后的部署包（已加入 .gitignore，不入库）
```

## 快速开始

### 1. 下载所有公有包

```bash
bash package/download.sh
```

- 已存在且完整的文件（本地大小 ≥ Content-Length）自动跳过
- 私有包自动跳过并在末尾列出提示
- 下载失败以非零状态码退出，可重复执行直到全部完成

### 2. 校验解压目录名

```bash
python3 package/verify_decompress.py
```

对每个已下载的包：
- 读取压缩包内实际顶层目录名
- 与 `service_ddl.json` 中的 `decompressPackageName` 比对
- 不一致时自动写回 JSON 文件（多架构服务的归一化名称不修改）

### 3. 手动下载单个包

```bash
# 示例：手动重试 flink（代理环境可能需要）
curl -L --max-time 3600 -C - \
  -o package/flink-2.2.1-bin-scala_2.12.tgz \
  "https://archive.apache.org/dist/flink/flink-2.2.1/flink-2.2.1-bin-scala_2.12.tgz"
```

## manifest.json 字段说明

| 字段 | 说明 |
|---|---|
| `service` | 服务名（对应 `meta/datacluster/<SERVICE>/`） |
| `arch` | 架构（`x86_64` / `aarch64` / `common`） |
| `packageName` | 包文件名，同时作为下载目标文件名 |
| `decompressPackageName` | 解压后安装目录名，对应 `service_ddl.json` 中同名字段 |
| `downloadUrl` | 下载地址；`null` 表示私有包，需手动上传 |
| `status` | `public`（可自动下载）/ `private`（需手动上传） |
| `note` | 备注（私有包说明、镜像地址等） |

## 私有包

以下包为内部构建，无公开下载地址，需从内部 Nexus 手动上传到本目录：

| 包文件名 | 说明 |
|---|---|
| `datart-server.tar.gz` | 内部 BI 服务 |
| `redis-8.6.tar.gz` / `redis-8.6-arm.tar.gz` | 自定义构建（非官方版本号） |

## 多架构包说明

ALERTMANAGER、DORIS、PROMETHEUS 各有 x86_64 和 aarch64 两个包，包内顶层目录名含架构后缀（如 `alertmanager-0.32.1.linux-amd64`）。Worker 安装时使用 `tar --strip-components=1` 剥除顶层目录，因此 `decompressPackageName` 使用归一化名称（如 `alertmanager-0.32.1`），与实际架构无关。

## 更新清单

如需新增或修改组件版本：

1. 编辑 `manifest.json`，更新 `packageName` / `downloadUrl` / `decompressPackageName`
2. 同步修改对应的 `datasophon-api/src/main/resources/meta/datacluster/<SERVICE>/service_ddl.json`
3. 运行 `download.sh` 下载新包
4. 运行 `verify_decompress.py` 校验目录名是否一致
