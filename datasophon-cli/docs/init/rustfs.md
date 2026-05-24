# init rustfs — 安装 Rustfs 对象存储

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitRustfs.java`

## 用途

在目标节点上安装并启动 [Rustfs](https://github.com/rustfs/rustfs) S3 兼容对象存储服务，作为 Datasophon 的统一文件存储后端（替代 MinIO）。

---

## 参数

独有参数 10 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-e` | `--enable` | 否 | `false` | `false` 时直接跳过。**必须设为 `true` 才会安装。** |
| `-pp` | `--packagePath` | **是** | — | 安装包目录 |
| `-in` | `installPath`（无 `--` 前缀，已知 bug） | **是** | — | 安装目标路径（Rustfs 安装到 `<installPath>/rustfs/`） |
| `-x` | `--x86Tar` | 否 | — | x86_64 Rustfs tar.gz 文件名 |
| `-a` | `--aarch64Tar` | 否 | — | aarch64 Rustfs tar.gz 文件名 |
| `-wh` | `--webHost` | **是** | — | Web 控制台绑定 IP 或 hostname |
| `-wp` | `--webPort` | **是** | — | Web 控制台端口（如 `9041`） |
| `-ap` | `--apiPort` | **是** | — | S3 API 端口（如 `9040`） |
| `-u` | `--username` | **是** | — | AccessKey（管理员用户名，如 `admin`） |
| `-p` | `--password` | **是** | — | SecretKey（管理员密码） |

> **已知问题**：`-in` 的 `@Option` 声明中 names 缺少 `--` 前缀，长名 `--installPath` 无法使用，只能用 `-in`。

---

## 示例

```bash
java -jar datasophon-cli.jar init rustfs \
  -e true \
  -pp /opt/datasophon/datasophon-init/packages \
  -in /opt/datasophon \
  -x rustfs-linux-x86_64-musl-1.0.0.tar.gz \
  -a rustfs-linux-aarch64-musl-1.0.0.tar.gz \
  -wh 192.168.2.132 \
  -wp 9041 \
  -ap 9040 \
  -u admin \
  -p <your-secret-key>
```

---

## 行为说明

### 启动命令（写入 `<installPath>/rustfs/start.sh`）

```bash
<installPath>/rustfs/rustfs \
  --address <webHost>:<apiPort> \
  --console-enable \
  --console-address <webHost>:<webPort> \
  --access-key <username> \
  --secret-key <password> \
  <dataPath> \
  > <logsPath>/rustfs.log 2>&1 &
```

### 安装路径结构

```
<installPath>/rustfs/
├── rustfs          ← 主二进制文件
├── data/           ← 数据目录
├── logs/           ← 日志目录
└── start.sh        ← 启动脚本
```

### 安装步骤

1. `-e=false` 时直接跳过。
2. 检查 `<installPath>/rustfs/` 目录是否存在，不存在则：
   - 解压 tar.gz，重命名二进制目录为 `rustfs/`
   - 创建 `data/` 和 `logs/` 目录
3. 检查 Rustfs 进程（`ps -ef | grep rustfs | grep -v grep`），未运行则执行 `start.sh`。
4. `sleep 3` 等待启动，再次检查进程，仍未运行则抛出 `ExecutionException`。

---

## 与 cluster-sample.yml 的对应关系

```yaml
global:
  rustfs:
    enable: true
    config:
      webPort: 9041
      apiPort: 9040
      user: "admin"
      password: "..."       # jasypt 加密
      installType: "SNSD"   # SNSD/SNMD/MNMD
      volumes: "/data/rustfs0"
    nodes: ["app6"]         # 只在此节点安装
```

---

## 注意事项

- 当前实现不支持 `installType` 参数（yaml 中有定义但 CLI 未读取），始终以单进程模式启动。
- 生产集群建议使用 `MNMD`（多节点多磁盘）高可用模式，但需要手动配置 Rustfs。
- `create cluster` 编排中 Rustfs 在 Nexus `registry` 之前安装（Rustfs 先就绪，Nexus 用其做后端）。
