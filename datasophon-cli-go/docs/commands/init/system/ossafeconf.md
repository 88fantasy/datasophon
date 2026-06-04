# datasophon-cli init osSafeConf

## 用途

执行操作系统基线安全加固，包含 20 余项配置：禁用不必要的系统服务、限制 SSH 根登录参数、设置密码策略、调整文件权限等。适合企业合规要求较高的部署场景。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init osSafeConf [公共 flag]
```

## 参数 / Flags

此命令无自定义 flag。

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

无。

## 示例

### dry-run 预检（推荐先 dry-run，查看将执行的安全配置项）

```bash
datasophon-cli --dry-run init osSafeConf \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init osSafeConf \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

命令尽力而为，单项配置失败仅打印警告，整体不中断。

## 相关命令

- [`init system-conf`](./system-conf.md) — 系统性能参数调优（limits/sysctl）
- [DAG 步骤表](../../../reference/init-all-dag.md)

