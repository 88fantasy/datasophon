# datasophon-cli init jdk17

## 用途

在目标节点上安装 Eclipse Temurin OpenJDK 17.0.19，解压到 `--installPath`（与 docker、rustfs 等其他组件同处一个安装根目录），再软链到固定别名 `/usr/local/jdk17`，并配置 `JAVA17_HOME`。Datasophon Manager（API 服务）现已运行在 [JDK 21](./jdk21.md) 上；本命令保留供部分 K8s 相关组件按需使用。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init jdk17 \
  -p <packagePath> --installPath <dir> [公共 flag]
```

## 参数 / Flags

|      flag       |  简写  |   类型   | 默认 | 必填 |          说明          |
|-----------------|------|--------|----|----|----------------------|
| `--packagePath` | `-p` | string | —  | 是  | 包含 JDK 17 tar 包的目录路径 |
| `--installPath` | 无    | string | —  | 是  | 组件统一安装根目录，JDK 实际解压到此目录下（软链门面 `/usr/local/jdk17` 固定不变） |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 安装包与路径

tar 包文件名由 CLI 内置固定，需与 `--packagePath` 目录下的实际安装包一致（`package/manifest.json` 中 `JDK17` 条目同名，`download.sh` 会自动下载到 `package/raw/packages/`）：

|   架构    |                        文件名                        |
|---------|-----------------------------------------------------|
| x86_64  | `OpenJDK17U-jdk_x64_linux_hotspot_17.0.19_10.tar.gz`     |
| aarch64 | `OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.19_10.tar.gz` |

- **通过 Nexus 下载**：加 `--enableRegistry --registryIp <ip> --registryPort <port>`，CLI 从 `http://<ip>:<port>/repository/raw/packages/<文件名>` 拉取。
- **本地预置**：不加 `--enableRegistry` 时，要求 `--packagePath` 目录下已存在对应 tar 文件。

解压后 CLI 会把 Temurin 实际解压出的目录（`jdk-17.0.19+10`，位于 `--installPath` 下）软链到固定别名 `/usr/local/jdk17`，与具体补丁版本号解耦；已安装（存在 `bin/java`）时跳过重复安装。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init jdk17 \
  -p /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /data/install_datasophon \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行（本地预置包）

```bash
datasophon-cli init jdk17 \
  -p /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /data/install_datasophon \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 通过 Nexus 下载

```bash
datasophon-cli init jdk17 \
  -p /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /data/install_datasophon \
  --enableRegistry --registryIp 192.168.1.10 --registryPort 8091 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|                   错误信息                   |         根因          |            处置             |
|------------------------------------------|---------------------|----------------------------|
| `required flag(s) "packagePath" not set` | 未提供 `-p`            | 补上参数                       |
| `required flag(s) "installPath" not set` | 未提供 `--installPath` | 补上参数                       |
| `安装包不存在`                                 | 指定目录下找不到 JDK 17 tar | 确认文件名与本页「安装包与路径」一致        |

## 相关命令

- [`init jdk8`](./jdk8.md) — 安装 JDK 8（大数据组件依赖）
- [DAG 步骤表](../../../reference/init-all-dag.md)

