# datasophon-cli create config

## 用途

从内置 YAML 模板生成 `cluster-sample.yml` 集群配置文件，并自动填充：

- `RegistryPassword` / `MySQLRootPassword` / `RustfsPassword` — 随机生成的 12 位安全密码（字符集含大小写字母、数字和 `@#$%&*+-_`）
- `CurrentNodeIP` — 当前执行机器的本机 IPv4 地址（枚举非 loopback 网卡，取第一个 IPv4）

生成后**必须手动编辑**以下内容（密码外的其他字段均为示例值）：
- 节点 IP、hostname、SSH 端口/密码
- 各服务的 `node` 字段（指向正确的 hostname）
- 安装包文件名（`global.packages.*`）

## 用法 (Synopsis)

```bash
datasophon-cli create config [flags]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--output` | `-o` | string | `cluster-config.yml` | 否 | 输出文件路径（相对或绝对均可） |
| `--force` | `-f` | bool | `false` | 否 | 文件已存在时强制覆盖 |

> 不继承 init 公共 flag；也不需要 `--dry-run`（仅生成文件，无 SSH 操作）。

## 配置文件依赖

无（本命令生成配置文件，不读取配置文件）。

## 示例

### 生成到默认路径

```bash
datasophon-cli create config
# 输出到当前目录的 cluster-config.yml
```

### 生成到 datasophon-init/config/ 目录

```bash
datasophon-cli create config \
  --output /data/datasophon/datasophon-init/config/cluster-sample.yml
```

### 强制覆盖已存在文件

```bash
datasophon-cli create config \
  --output /data/datasophon/datasophon-init/config/cluster-sample.yml \
  --force
```

输出示例：

```
配置文件已生成: /data/datasophon/datasophon-init/config/cluster-sample.yml
本机 IP: 192.168.1.10
⚠️  密码已随机生成并写入配置文件，请妥善保管
```

> 生成的文件权限为 `0600`（仅 owner 可读），防止密码泄露。

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `输出文件已存在: <path>` | 文件已存在且未指定 `--force` | 加 `--force` 或先备份旧文件 |
| `未找到非 loopback 的 IPv4 地址` | 机器无可用网卡或网卡未启动 | 手动在生成的配置文件中填写 `nodes[0].ip` |

## 相关命令

- [`create cluster`](./cluster.md) — 使用生成的配置文件初始化集群
- [配置文件参考](../../config-reference.md) — 所有字段详解
