# datasophon-cli init jdk21

## 用途

在目标节点上安装 Eclipse Temurin OpenJDK 21.0.11，解压到 `--installPath`（与 docker、rustfs 等其他组件同处一个安装根目录），再软链到固定别名 `/usr/local/jdk21`，并配置 `JAVA_HOME`。Datasophon Manager（API 服务）与 Worker 现运行在 JDK 21 上，是 `initALL` DAG 中平台自身运行时的标准 JDK 安装步骤（取代原 `init jdk17`）。加 `--enableRegistry` 时 tar 包从 Nexus（`package/manifest.json` 中 `JDK21` 条目）下载。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init jdk21 \
  -p <packagePath> --installPath <dir> [公共 flag]
```

## 参数 / Flags

|      flag       |  简写  |   类型   | 默认 | 必填 |          说明          |
|-----------------|------|--------|----|----|----------------------|
| `--packagePath` | `-p` | string | —  | 是  | 包含 JDK 21 tar 包的目录路径 |
| `--installPath` | 无    | string | —  | 是  | 组件统一安装根目录，JDK 实际解压到此目录下（软链门面 `/usr/local/jdk21` 固定不变） |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 安装包与路径

tar 包文件名由 CLI 内置固定，需与 `--packagePath` 目录下的实际安装包一致（`package/manifest.json` 中 `JDK21` 条目同名，`download.sh` 会自动下载到 `package/raw/packages/`）：

|   架构    |                        文件名                        |
|---------|-----------------------------------------------------|
| x86_64  | `OpenJDK21U-jdk_x64_linux_hotspot_21.0.11_10.tar.gz`     |
| aarch64 | `OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.11_10.tar.gz` |

- **通过 Nexus 下载**（推荐，见下方示例）：加 `--enableRegistry --registryIp <ip> --registryPort <port>`，CLI 从 `http://<ip>:<port>/repository/raw/packages/<文件名>` 拉取；需先用 `datasophon-cli upload registry` 把 `package/raw/packages/` 下的 JDK 包上传到 Nexus。
- **本地预置**：不加 `--enableRegistry` 时，要求 `--packagePath` 目录下已存在对应 tar 文件。

解压后 CLI 会把 Temurin 实际解压出的目录（`jdk-21.0.11+10`，位于 `--installPath` 下）软链到固定别名 `/usr/local/jdk21`，与具体补丁版本号解耦；已安装（存在 `bin/java`）时跳过重复安装。若升级到其他 21.x 补丁版本，需同步更新 `package/manifest.json` 的 `JDK21` 条目与 CLI 源码中的版本常量。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init jdk21 \
  -p /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /data/install_datasophon \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行（本地预置包）

```bash
datasophon-cli init jdk21 \
  -p /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /data/install_datasophon \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 通过 Nexus 下载

```bash
datasophon-cli init jdk21 \
  -p /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /data/install_datasophon \
  --enableRegistry --registryIp 192.168.1.10 --registryPort 8091 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|                   错误信息                   |         根因          |              处置              |
|------------------------------------------|---------------------|-------------------------------|
| `required flag(s) "packagePath" not set` | 未提供 `-p`            | 补上参数                          |
| `required flag(s) "installPath" not set` | 未提供 `--installPath` | 补上参数                          |
| `安装包不存在`                                 | 指定目录下找不到 JDK 21 tar | 确认文件名与本页「安装包与路径」一致           |

## 相关命令

- [`init jdk8`](./jdk8.md) — 安装 JDK 8（大数据组件依赖）
- [`init jdk17`](./jdk17.md) — 安装 JDK 17（K8s 场景遗留，平台自身已不再依赖）
- [DAG 步骤表](../../../reference/init-all-dag.md)
