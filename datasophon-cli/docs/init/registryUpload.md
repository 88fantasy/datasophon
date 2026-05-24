# init registryUpload — 上传安装包到 Nexus

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitRegistryUpload.java`

## 用途

将本地安装包目录中的所有文件批量上传到 Nexus 制品库（raw/yum/apt/docker 仓库），以供集群其他节点通过 Nexus 拉取。Docker 镜像通过 `docker load + docker push` 方式上传。

---

## 参数

独有参数 9 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-t` | `--type` | 否 | `nexus` | 制品库类型（当前只支持 `nexus`） |
| `-pn` | `--productPackagesPath` | **是** | — | 产品安装包目录（全量）绝对路径 |
| `-wh` | `--webHost` | **是** | — | Nexus Web 地址（hostname 或 IP） |
| `-wp` | `--webPort` | **是** | — | Nexus Web 端口 |
| `-u` | `--username` | **是** | — | Nexus 管理员用户名 |
| `-p` | `--password` | **是** | — | Nexus 管理员密码 |
| `-e` | `--isSuccessDelete` | 否 | `false` | 上传成功后是否删除本地文件 |
| `-disu` | `--disableUploadRegistry` | 否 | `false` | `true` 时完全跳过上传（仅调试用） |
| `-dp` | `--dockerHttpPort` | **是** | — | Docker 仓库 HTTP 端口 |

---

## 示例

```bash
# 上传安装包（保留本地文件）
java -jar datasophon-cli.jar init registryUpload \
  -pn /opt/packages/product \
  -wh app6 \
  -wp 8091 \
  -u admin \
  -p <your-nexus-password> \
  -dp 8083 \
  -enableR true \
  -rip app6 \
  -rport 8091 \
  -rusername admin \
  -rpassword <your-nexus-password>

# 上传后删除本地文件（节省磁盘）
java -jar datasophon-cli.jar init registryUpload \
  -pn /opt/packages/product \
  -wh app6 \
  -wp 8091 \
  -u admin \
  -p <your-nexus-password> \
  -dp 8083 \
  -e true \
  -enableR true \
  -rip app6 \
  -rport 8091 \
  -rusername admin \
  -rpassword <your-nexus-password>
```

---

## 行为说明

### 上传流程

1. 若 `-enableR=false`，命令跳过。
2. 若 `-disu=true`，命令跳过（调试模式）。
3. 调用 `NexusFileUtils.repositoryUploadBatch()` 批量上传 `-pn` 目录下的文件，日志输出：
   - 耗时（秒）
   - 成功数量 / 失败数量
4. Docker 镜像处理（`<pn>/docker/` 子目录存在时）：
   - 遍历目录下所有 tar 文件
   - `docker load -i <file>` 载入镜像
   - `docker tag <imageId> <registryIp>:<dockerHttpPort>/docker/<imageName>`
   - `docker push <tagged-image>` 推送到 Nexus Docker 仓库

### 目录结构约定

```
productPackagesPath/
├── raw/
│   └── packages/       ← raw 仓库文件（rpm/tar.gz/jar 等）
├── yum/                ← yum 仓库文件
├── apt/                ← apt 仓库文件
└── docker/             ← Docker 镜像 tar 文件（.tar）
```

---

## 注意事项

- Docker 上传需要目标节点已安装并运行 Docker，且已登录 Nexus Docker Registry（`docker login`）。
- `create cluster initALL` 中此命令在 Nexus（`registry`）安装完成后、对**本地 Master 节点**执行一次。
- 安装包目录文件较多时耗时可能超过 30 分钟，请确保网络和磁盘 IO 正常。
