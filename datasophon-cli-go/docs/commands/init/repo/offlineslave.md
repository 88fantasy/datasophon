# datasophon-cli init offlineSlave

## 用途

在目标节点上配置 yum/apt 软件源，指向 [`create yum-server`](../../create/yum-server.md) 安装的 httpd/apache2 离线源（注：底层 Task 仍叫 `InitOfflineServer`，但 CLI 入口已移到 `create yum-server`）。使节点的 `yum install` / `apt-get install` 能从本地网络拉包，无需公网访问。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init offlineSlave \
  --serverIp <ip> \
  --serverPort <port> \
  [公共 flag]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--serverIp` | 无 | string | — | 是 | 离线源服务器 IP（对应 `create yum-server` 的 `--serverIp`） |
| `--serverPort` | 无 | string | — | 是 | 离线源 HTTP 端口 |

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

无（服务器地址通过 flag 传入）。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init offlineSlave \
  --serverIp 192.168.1.10 \
  --serverPort 8080 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init offlineSlave \
  --serverIp 192.168.1.10 \
  --serverPort 8080 \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `yum/apt 测试失败` | 离线源不可达 | 确认 `create yum-server` 已成功且端口可访问 |

## 相关命令

- [`create yum-server`](../../create/yum-server.md) — 先在离线源节点启动 httpd/apache2 服务
- [DAG 步骤表](../../../reference/init-all-dag.md)
