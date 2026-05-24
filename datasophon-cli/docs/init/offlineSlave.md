# init offlineSlave — 配置节点使用离线源

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitOfflineSlave.java`

## 用途

在目标节点上将 yum/apt 源指向已搭建好的离线 HTTP 服务器（或 Nexus 制品库），使节点能通过本地网络安装软件包：

- CentOS/OpenEuler：生成 `/etc/yum.repos.d/local_base.repo`，执行 `yum clean all && yum makecache`
- Ubuntu/Debian：生成 `/etc/apt/sources.list`，执行 `apt clean && apt update`

---

## 参数

独有参数 2 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-ip` | `--serverIp` | **是** | — | 离线源服务器 IP（httpd 或 Nexus 节点 hostname/IP） |
| `-port` | `--serverPort` | **是** | — | 离线源端口（httpd: 如 `4080`，Nexus: 如 `8091`） |

### 制品库模式（`-enableR true`）

开启制品库时，IP/Port 会被重新路由到 Nexus 的 yum/apt 仓库地址：

```
http://<registryUsername>:<registryPassword>@<serverIp>:<serverPort>/repository/yum/<arch>/<osType>/
```

> 用户名/密码会 URL-encode，确保特殊字符安全传输。

---

## 示例

### 普通离线源模式（httpd）

```bash
java -jar datasophon-cli.jar init offlineSlave \
  -ip 192.168.2.43 \
  -port 4080
```

### Nexus 制品库模式

```bash
java -jar datasophon-cli.jar init offlineSlave \
  -ip 192.168.2.43 \
  -port 8091 \
  -enableR true \
  -rip 192.168.2.43 \
  -rport 8091 \
  -rusername admin \
  -rpassword <your-password>
```

---

## 行为说明

源 URL 构造逻辑：

| 模式 | URL 格式 |
|---|---|
| 普通（httpd） | `http://<serverIp>:<serverPort>/<arch>/<osType>/` |
| Nexus（CentOS） | `http://<user>:<pwd>@<serverIp>:<serverPort>/repository/yum/<arch>/<osType>/` |
| Nexus（Ubuntu） | `http://<user>:<pwd>@<serverIp>:<serverPort>/repository/apt/` |

`apt update` / `yum makecache` 失败时抛出 `RuntimeException`（不继续）。

---

## 注意事项

- 在 `create cluster` 编排中，`offlineSlave` 在 `offlineServer`（或 Nexus `registry`）就绪后，对**所有节点**执行。
- `apt` 配置前会备份 `/etc/apt/sources.list` 和 `rightscale_extra.sources.list`。
- 需要先执行 `dpkg --configure -a` 修复可能存在的 dpkg 锁定问题（Ubuntu）。
