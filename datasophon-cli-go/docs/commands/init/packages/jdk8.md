# datasophon-cli init jdk8

## 用途

在目标节点上安装 JDK 8，解压到 `/usr/local/jdk1.8.0_333/`，并配置 `JAVA_HOME` 环境变量。Datasophon 大数据组件（HDFS、YARN、HBase 等）运行时依赖 JDK 8。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init jdk8 \
  --packagePath <dir> [公共 flag]
```

## 参数 / Flags

|      flag       | 简写 |   类型   | 默认 | 必填 |         说明          |
|-----------------|----|--------|----|----|---------------------|
| `--packagePath` | 无  | string | —  | 是  | 包含 JDK 8 tar 包的目录路径 |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

|               字段               |          说明          |
|--------------------------------|----------------------|
| `global.packages.jdk8.x86_64`  | x86_64 架构 JDK 8 文件名  |
| `global.packages.jdk8.aarch64` | aarch64 架构 JDK 8 文件名 |

安装路径固定为 `/usr/local/jdk1.8.0_333/`。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init jdk8 \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init jdk8 \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|                   错误信息                   |         根因          |                处置                |
|------------------------------------------|---------------------|----------------------------------|
| `required flag(s) "packagePath" not set` | 未提供 `--packagePath` | 补上参数                             |
| `安装包不存在`                                 | 指定目录下找不到 JDK 8 tar  | 确认文件名与 `global.packages.jdk8` 一致 |

## 相关命令

- [`init jdk17`](./jdk17.md) — 安装 JDK 17（K8s 场景）
- [DAG 步骤表](../../../reference/init-all-dag.md)

