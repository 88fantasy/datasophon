# datasophon-cli init osUser

## 用途

在目标节点上创建 `hadoop` 用户组和 `hadoop` 用户（若已存在则跳过），并配置 `sudo` 权限。所有 Datasophon 组件以 `hadoop` 用户身份运行。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init osUser [公共 flag]
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
datasophon-cli --dry-run init osUser \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init osUser \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

| 情况 | 说明 |
|---|---|
| hadoop 用户已存在 | 跳过创建，打印 Info 日志，不报错 |

## 相关命令

- [DAG 步骤表](../../../reference/init-all-dag.md)
