# datasophon-cli init nmap

## 用途

在目标节点上安装 `nmap` 工具，供后续端口连通性检测使用（如 SSH 可达性验证）。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init nmap [公共 flag]
```

## 参数 / Flags

此命令无自定义 flag。

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

无。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init nmap \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init nmap \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `yum install nmap` 失败 | 无可用软件源 | 先配置离线源（`init offlineServer` / `init offlineSlave`） |

## 相关命令

- [DAG 步骤表](../../../reference/init-all-dag.md)
