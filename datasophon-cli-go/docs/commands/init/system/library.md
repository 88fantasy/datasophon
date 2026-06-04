# datasophon-cli init library

## 用途

安装组件运行时依赖库（如 `libtirpc-devel`、`libzstd`、`perl` 等），并根据节点 OS 类型自动选择包管理器（yum / apt）。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] init library [公共 flag]
```

## 参数 / Flags

此命令无自定义 flag。

> 继承 init 公共 flag（`--config`、`--registryIp` 等）—— 详见 [global-flags.md#init-公共-flag](../../../global-flags.md#init-公共-flag)
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../../global-flags.md)

## 配置文件依赖

无（OS 类型在运行时自动检测）。

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run init library \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 实际执行

```bash
datasophon-cli init library \
  --config /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|         错误信息         |        根因        |                            处置                            |
|----------------------|------------------|----------------------------------------------------------|
| `yum install` 失败     | 离线环境无 yum 源      | 先执行 `create yum-server` 启动离线源 + `init offlineSlave` 配置节点 |
| `apt-get install` 失败 | Ubuntu 节点无 apt 源 | 同上                                                       |

## 相关命令

- [`create yum-server`](../../create/yum-server.md) — 启动 httpd/apache2 离线 yum/apt 源
- [`init offlineSlave`](../repo/offlineslave.md) — 节点侧消费离线源
- [DAG 步骤表](../../../reference/init-all-dag.md)

