# init jdk17 — 安装 OpenJDK 17.0.1

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitJdk17.java`

## 用途

在目标节点上安装 OpenJDK 17.0.1，解压到 `/usr/local/jdk-17.0.1`，并配置 `JAVA17_HOME` 环境变量。Datasophon Worker 进程使用此 JDK 运行。

---

## 参数

独有参数 1 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-pp` | `--packagePath` | **是** | — | 安装包目录，需含 JDK 17 tar.gz |

---

## 安装包文件名

| 架构 | 文件名 |
|---|---|
| x86_64 | `openjdk-17.0.1_linux-x64_bin.tar.gz` |
| aarch64 | `openjdk-17.0.1_linux-aarch64_bin.tar.gz` |

---

## 示例

```bash
# 从本地包安装
java -jar datasophon-cli.jar init jdk17 \
  -pp /opt/datasophon/datasophon-init/packages

# 从 Nexus 制品库下载安装包
java -jar datasophon-cli.jar init jdk17 \
  -pp /opt/datasophon/datasophon-init/packages \
  -enableR true \
  -rip 192.168.2.43 \
  -rport 8091 \
  -rusername admin \
  -rpassword <your-password>
```

---

## 行为说明

1. 检查 `/usr/local/jdk-17.0.1/bin/java` 是否存在，已安装则跳过。
2. 若启用 Nexus，从制品库下载；否则使用本地包。
3. 清理 `/etc/profile` 中旧的 `JAVA17_HOME`/`CLASSPATH`/`PATH` 行。
4. 解压 tar.gz 到 `/usr/local/`。
5. 向 `/etc/profile` 追加：
   ```bash
   export JAVA17_HOME=/usr/local/jdk-17.0.1
   ```
6. 在 `~/.bash_profile` 和 `~/.bashrc` 追加 `source /etc/profile`。

> **注意**：JDK 17 只设置 `JAVA17_HOME`，**不设置 `JAVA_HOME`**——避免覆盖 JDK 8 的主 JAVA_HOME。Worker 进程通过 `JAVA17_HOME` 显式指定。

---

## 注意事项

- 与 `init jdk8` 不同，此命令不安装 BouncyCastle，也不修改 TLS 策略。
- 同时安装 JDK 8 和 JDK 17 是正常使用场景：JDK 8 供大数据组件（HDFS/YARN 等）使用，JDK 17 供 Datasophon Worker 使用。
- `create cluster` 编排中执行顺序：`jdk8` → `jdk17`（两者均对非本地节点执行）。
