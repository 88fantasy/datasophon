# datasophon-cli init rustfs

## 用途

在目标节点上安装并启动 RustFS 对象存储服务。RustFS 是 Datasophon 可选的内置 S3 兼容存储，用于替代 MinIO。当 `--enable` 为 `false` 时跳过。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init rustfs \
  -e \
  --packagePath <dir> \
  --installPath <dir> \
  --webHost <host> \
  --webPort <port> \
  --apiPort <port> \
  -u <username> \
  -p <password> \
  [公共 flag]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--enable` | `-e` | bool | `false` | 否 | 设为 `true` 启用 RustFS 安装（默认跳过） |
| `--packagePath` | 无 | string | — | 是 | 安装包目录 |
| `--installPath` | 无 | string | — | 是 | 安装根目录 |
| `--webHost` | 无 | string | — | 是 | RustFS Web UI 主机 |
| `--webPort` | 无 | string | — | 是 | RustFS Web UI 端口 |
| `--apiPort` | 无 | string | — | 是 | RustFS API 端口 |
| `--username` | `-u` | string | — | 是 | RustFS 管理员用户名 |
| `--password` | `-p` | string | — | 是 | RustFS 管理员密码 |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

| 字段 | 说明 |
|---|---|
| `global.rustfs.enable` | 若为 true，在 DAG 中触发此步骤 |
| `global.rustfs.node` | RustFS 安装节点（DAG 节点选择器） |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init rustfs \
  -e \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/rustfs \
  --webHost 192.168.1.10 \
  --webPort 9001 \
  --apiPort 9000 \
  -u admin \
  -p 'YourPassword' \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init rustfs \
  -e \
  --packagePath /data/datasophon/datasophon-init/packages/raw/packages \
  --installPath /opt/rustfs \
  --webHost 192.168.1.10 \
  --webPort 9001 \
  --apiPort 9000 \
  -u admin \
  -p 'YourPassword' \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 情况 | 说明 |
|---|---|
| `enable=false` | 跳过，打印 Info 日志，不报错 |
| `安装包不存在` | `--packagePath` 下未找到 RustFS 包 | 确认 `global.packages.rustfs` 文件名正确 |

## 相关命令

- [DAG 步骤表](../../../reference/init-all-dag.md)
- [配置文件参考 — global.rustfs](../../../config-reference.md)
