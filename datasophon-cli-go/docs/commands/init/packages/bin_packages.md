# datasophon-cli init bin_packages

## 用途

将本机的 `datasophon-init` 二进制目录（含初始化脚本、配置模板等）发送到目标节点的指定安装路径，并在目标节点创建相应的目录结构。这是分发初始化工具包的关键步骤。

> **注意**：命令名为 `bin_packages`，不是 `bin_package`（旧版 README 错误写法）。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init bin_packages \
  -i <datasophonInitPath> \
  -n <installPath> \
  [--initPathOverwriteForce] \
  [公共 flag]
```

## 参数 / Flags

|            flag            |  简写  |   类型   |   默认    | 必填 |             说明             |
|----------------------------|------|--------|---------|----|----------------------------|
| `--datasophonInitPath`     | `-i` | string | —       | 是  | 本地 datasophon-init 目录路径（源） |
| `--installPath`            | `-n` | string | —       | 是  | 目标节点安装根目录（目的地）             |
| `--initPathOverwriteForce` | 无    | bool   | `false` | 否  | 目标路径已存在时是否强制覆盖             |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

无。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init bin_packages \
  -i /data/datasophon/datasophon-init \
  -n /opt/datasophon \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init bin_packages \
  -i /data/datasophon/datasophon-init \
  -n /opt/datasophon \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 强制覆盖已有目录

```bash
datasophon-cli init bin_packages \
  -i /data/datasophon/datasophon-init \
  -n /opt/datasophon \
  --initPathOverwriteForce \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|                      错误信息                       |    根因     |                 处置                 |
|-------------------------------------------------|-----------|------------------------------------|
| `required flag(s) "datasophonInitPath" not set` | 未提供 `-i`  | 补上 `-i` 参数                         |
| `目标路径已存在`                                       | 目标路径已有旧版本 | 加 `--initPathOverwriteForce` 或手动删除 |

## 相关命令

- [DAG 步骤表](../../../reference/init-all-dag.md)

