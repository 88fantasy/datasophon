# init jdk8 — 安装 JDK 8u333

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitJdk8.java`

## 用途

在目标节点上安装 JDK 8（1.8.0_333），解压到 `/usr/local/jdk1.8.0_333`，并配置 `JAVA_HOME`/`JAVA8_HOME` 环境变量。同时安装 BouncyCastle 加密库（`bcprov-jdk15on-1.68.jar`）和调整 TLS 安全策略。

---

## 参数

独有参数 1 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-pp` | `--packagePath` | **是** | — | 安装包目录，需含 JDK tar.gz 和 bcprov jar |

---

## 安装包文件名

| 架构 | 文件名 |
|---|---|
| x86_64 | `jdk-8u333-linux-x64.tar.gz` |
| aarch64 | `jdk-8u333-linux-aarch64.tar.gz` |

BouncyCastle 补充包：`bcprov-jdk15on-1.68.jar`（两种架构相同）

---

## 示例

```bash
# 从本地包安装
java -jar datasophon-cli.jar init jdk8 \
  -pp /opt/datasophon/datasophon-init/packages

# 从 Nexus 制品库下载安装包
java -jar datasophon-cli.jar init jdk8 \
  -pp /opt/datasophon/datasophon-init/packages \
  -enableR true \
  -rip 192.168.2.43 \
  -rport 8091 \
  -rusername admin \
  -rpassword <your-password>
```

---

## 行为说明

1. 检查 `/usr/local/jdk1.8.0_333/bin/java` 是否存在（等价于 `which java` 指向正确路径）。
2. 若已安装则跳过所有步骤。
3. 若启用 Nexus（`-enableR true`），从制品库 `raw/packages/` 仓库下载安装包；否则直接使用 `-pp` 本地包。
4. 清理 `/etc/profile` 中旧的 `JAVA_HOME`/`CLASSPATH` 行，解压 tar.gz 到 `/usr/local/`。
5. 向 `/etc/profile` 追加：
   ```bash
   export JAVA_HOME=/usr/local/jdk1.8.0_333
   export JAVA8_HOME=/usr/local/jdk1.8.0_333
   export PATH=$PATH:$JAVA_HOME/bin
   ```
6. 在 `~/.bash_profile` 和 `~/.bashrc` 追加 `source /etc/profile`。
7. 复制 `bcprov-jdk15on-1.68.jar` 到 `$JAVA_HOME/jre/lib/ext/`。
8. 修改 `java.security`，解除 TLSv1 / TLSv1.1 的限制（离线环境兼容性）。

---

## 注意事项

- 已安装检测路径为 `/usr/local/jdk1.8.0_333/bin/java`（非 `which java`），升级时先手动删除旧目录。
- `/etc/profile` 变更后当前 SSH 会话不自动生效，需 `source /etc/profile` 或重新登录。
- `create cluster` 编排中，此命令只对**非本地 Worker 节点**执行（本地 Master 已预装 JDK）。
