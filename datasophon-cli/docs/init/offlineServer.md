# init offlineServer — 搭建离线 yum/apt HTTP 源服务器

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitOfflineServer.java`

## 用途

在目标节点上搭建离线软件包 HTTP 服务器（Apache httpd 或 apache2），将本地解压的 OS 软件包目录作为 yum/apt 源对外暴露：

```
packagePath/os/<arch>/<osType>/   ← 离线 rpm/deb 目录
                ↓ HTTP 暴露为
http://<serverIp>:<serverPort>/<arch>/<osType>/
```

---

## 参数

独有参数 3 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-p` | `--packagePath` | **是** | — | 安装包根目录（须含 `os/<arch>/<osType>` 子目录） |
| `-ip` | `--serverIp` | **是** | — | 离线源服务 IP（绑定 httpd 监听地址） |
| `-port` | `--serverPort` | **是** | — | 离线源服务端口（对应 yml 中 `global.yumServer.listenPort`） |

---

## 示例

```bash
java -jar datasophon-cli.jar init offlineServer \
  -p /opt/datasophon/datasophon-init/packages \
  -ip 192.168.2.43 \
  -port 4080
```

---

## 行为说明

### CentOS/OpenEuler（yum）

1. 配置 file:// 本地源（`/etc/yum.repos.d/local_base.repo`）
2. `yum clean all && yum makecache`
3. `yum install -y httpd`
4. 修改 httpd.conf：`DocumentRoot`、`<Directory>`、`Listen <port>`
5. `systemctl restart httpd`

### Ubuntu/Debian（apt）

1. 配置 file:// 本地源（`/etc/apt/sources.list`）
2. `apt update && apt -y install apache2`
3. 修改 `000-default.conf`、`apache2.conf`、`ports.conf`
4. `systemctl restart apache2`

---

## 注意事项

- **当 `-enableR true`（Nexus 模式）时**，此命令直接 skip（`return true`）——Nexus 已经包含 yum/apt 仓库，不需要额外搭建 httpd。
- `packagePath/os/` 目录必须存在且包含对应架构和 OS 的子目录，否则抛出 `ExecutionException`。
- 原有 yum.repos.d 目录会被整体备份（改名加时间戳），避免源冲突。
