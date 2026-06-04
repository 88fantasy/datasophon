# datasophon-cli init jdk17

## 用途

在目标节点上安装 JDK 17，解压到 `/usr/local/jdk-17.0.1/`，并配置 `JAVA_HOME`。Datasophon Manager（API 服务）运行在 JDK 17 上；K8s 相关组件也可能需要此版本。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init jdk17 \
  -p <packagePath> [公共 flag]
```

## 参数 / Flags

|      flag       |  简写  |   类型   | 默认 | 必填 |          说明          |
|-----------------|------|--------|----|----|----------------------|
| `--packagePath` | `-p` | string | —  | 是  | 包含 JDK 17 tar 包的目录路径 |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

|               字段                |          说明           |
|---------------------------------|-----------------------|
| `global.packages.jdk17.x86_64`  | x86_64 架构 JDK 17 文件名  |
| `global.packages.jdk17.aarch64` | aarch64 架构 JDK 17 文件名 |

安装路径固定为 `/usr/local/jdk-17.0.1/`。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init jdk17 \
  -p /data/datasophon/datasophon-init/packages/raw/packages \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init jdk17 \
  -p /data/datasophon/datasophon-init/packages/raw/packages \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|                   错误信息                   |         根因          |                处置                 |
|------------------------------------------|---------------------|-----------------------------------|
| `required flag(s) "packagePath" not set` | 未提供 `-p`            | 补上参数                              |
| `安装包不存在`                                 | 指定目录下找不到 JDK 17 tar | 确认文件名与 `global.packages.jdk17` 一致 |

## 相关命令

- [`init jdk8`](./jdk8.md) — 安装 JDK 8（大数据组件依赖）
- [DAG 步骤表](../../../reference/init-all-dag.md)

