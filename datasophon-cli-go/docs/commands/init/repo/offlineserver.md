# datasophon-cli init offlineServer

## 用途

在目标节点上启动离线软件源服务（Nginx 静态文件服务器），将本地 `packagePath` 下的 RPM/DEB 包通过 HTTP 提供给其他节点。当 `--enableRegistry` 为 `true` 时，此命令会跳过（由 Nexus 代替离线源）。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init offlineServer \
  -p <packagePath> \
  --serverIp <ip> \
  --serverPort <port> \
  [公共 flag]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--packagePath` | `-p` | string | — | 是 | 本地软件包根目录（Nginx 静态根目录） |
| `--serverIp` | 无 | string | — | 是 | 离线源服务器 IP |
| `--serverPort` | 无 | string | — | 是 | Nginx 监听端口 |

> 继承 init 公共 flag（`--config`、`--registryIp`、`--enableRegistry` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

> **注意**：若公共 flag `--enableRegistry` 为 `true`，此命令自动跳过。

## 配置文件依赖

| 字段 | 说明 |
|---|---|
| `global.registry.enable` | 若为 true，跳过离线源安装 |

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init offlineServer \
  -p /data/datasophon/datasophon-init/packages \
  --serverIp 192.168.1.10 \
  --serverPort 8080 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init offlineServer \
  -p /data/datasophon/datasophon-init/packages \
  --serverIp 192.168.1.10 \
  --serverPort 8080 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `enableRegistry=true，跳过离线源` | 已启用 Nexus | 正常，无需处理 |
| `Nginx 启动失败` | 端口被占用或 Nginx 未安装 | 检查端口、安装 Nginx |

## 相关命令

- [`init offlineSlave`](./offlineslave.md) — 配置节点使用此离线源
- [`upload registry`](../../upload/registry.md) — 使用 Nexus 替代离线源的上传路径
- [DAG 步骤表](../../../reference/init-all-dag.md)
