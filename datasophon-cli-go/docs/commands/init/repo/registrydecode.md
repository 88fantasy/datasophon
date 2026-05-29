# datasophon-cli init registryDecode

## 用途

将离线打包的 Nexus 制品库（编码后的包文件）解码并导入到本地 Nexus。适用于离线交付场景：先将 Nexus 内容打包（编码）随安装包一起交付，到目标环境后通过此命令解码还原。当 `--enable` 为 `false` 时跳过。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init registryDecode \
  -e \
  -d <datasophonHomePath> \
  --productConfigPath <path> \
  --productPackagesPath <path> \
  [公共 flag]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--enable` | `-e` | bool | `false` | 否 | 设为 `true` 启用解码流程（默认跳过） |
| `--datasophonHomePath` | `-d` | string | — | 是 | datasophon 根目录 |
| `--productConfigPath` | 无 | string | — | 是 | 产品配置文件路径 |
| `--productPackagesPath` | 无 | string | — | 是 | 离线包根目录路径 |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

> **注意**：`-e` 是布尔开关（presence = true），而非 `--enable=<值>`。

## 配置文件依赖

无（路径均通过 flag 传入）。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init registryDecode \
  -e \
  -d /data/datasophon \
  --productConfigPath /data/datasophon/config/product.yml \
  --productPackagesPath /data/datasophon/packages \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init registryDecode \
  -e \
  -d /data/datasophon \
  --productConfigPath /data/datasophon/config/product.yml \
  --productPackagesPath /data/datasophon/packages \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 情况 | 说明 |
|---|---|
| `enable=false` | 跳过，打印 Info 日志，不报错 |

## 相关命令

- [`upload registry`](../../upload/registry.md) — 另一种将包导入 Nexus 的方式（直接上传）
- [DAG 步骤表](../../../reference/init-all-dag.md)
