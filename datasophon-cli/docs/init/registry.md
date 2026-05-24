# init registry — 安装 Nexus 制品库

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitRegistry.java`

## 用途

在目标节点上安装 Sonatype Nexus Repository（Community Edition），作为整个集群的统一制品库，支持 yum、apt、raw、docker、helm 等仓库类型。安装后通过 REST API 自动初始化仓库。

---

## 参数

独有参数 11 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-t` | `--type` | 否 | `nexus` | 制品库类型（当前只支持 `nexus`） |
| `-pp` | `--packagePath` | **是** | — | 安装包目录 |
| `-in` | `installPath`（注意：无 `--` 前缀，属已知 bug） | **是** | — | Nexus 安装目标路径 |
| `-x` | `--x86Tar` | **是** | — | x86_64 Nexus tar.gz 文件名 |
| `-a` | `--aarch64Tar` | **是** | — | aarch64 Nexus tar.gz 文件名 |
| `-wh` | `--webHost` | **是** | — | Nexus Web 地址（hostname 或 IP） |
| `-wp` | `--webPort` | **是** | — | Nexus Web 端口（如 `8091`） |
| `-u` | `--username` | **是** | — | Nexus 管理员用户名（如 `admin`） |
| `-p` | `--password` | **是** | — | Nexus 管理员新密码（安装后自动修改初始密码） |
| `-r` | `--repositories` | **是** | — | 要初始化的仓库列表，逗号分隔（如 `yum,raw,apt,docker,helm`） |
| `-dp` | `--dockerHttpPort` | **是** | — | Docker 仓库 HTTP 端口（如 `8083`） |

> **已知问题**：`-in` 的 `@Option` 声明中 names 缺少 `--` 前缀（`names = {"-in", "installPath"}`），导致长名 `--installPath` 无法使用，只能用 `-in`。

---

## 示例

```bash
java -jar datasophon-cli.jar init registry \
  -pp /opt/datasophon/datasophon-init/packages \
  -in /opt/datasophon \
  -x nexus-3.85.0-03-linux-x86_64.tar.gz \
  -a nexus-3.85.0-03-linux-aarch_64.tar.gz \
  -wh app6 \
  -wp 8091 \
  -u admin \
  -p <your-nexus-password> \
  -r yum,raw,apt,docker,helm \
  -dp 8083 \
  -enableR true \
  -rip app6 \
  -rport 8091 \
  -rusername admin \
  -rpassword <your-nexus-password>
```

---

## 行为说明

### 安装流程

1. 若 `-enableR=false`，命令跳过（skip）—— 只有在 `enableRegistry=true` 时才真正安装。
2. 检查 `<installPath>/nexusDir/nexus` 是否存在，不存在则：
   - 创建目录，解压 tar.gz，重命名为 `nexus/`
   - 生成 `nexus-default.properties`（端口 + 监听地址）
3. 检查 Nexus 进程是否运行，未运行则启动并等待初始密码文件（`admin.password`）出现（最多等 200s）。
4. Nexus 就绪后通过 REST API 初始化：
   - 修改 admin 密码（`PUT /service/rest/v1/security/users/admin/change-password`）
   - 接受 EULA（`POST /service/rest/v1/system/eula`）
   - 按 `-r` 列表逐一创建仓库（yum/apt/raw/docker/helm hosted）
   - Docker 仓库额外配置 `DockerToken` Realm

### 生成的 nexus-default.properties

```
application-port=<webPort>
application-host=0.0.0.0
nexus-args=${jetty.etc}/jetty.xml,${jetty.etc}/jetty-http.xml,${jetty.etc}/jetty-requestlog.xml
nexus-context-path=/
```

---

## 注意事项

- Nexus 首次启动较慢（约 1-3 分钟），等待超时（200s）后若仍未就绪会抛出 RuntimeException。
- `create cluster` 中 registry 安装只在 `global.registry.node` 指定的**单个节点**执行。
- Nexus 社区版仅允许单节点，不支持高可用集群模式。
- 仓库名称固定由 `-r` 列表指定（如 `yum`、`apt`、`docker`），对应 Nexus 中的 hosted 仓库。
