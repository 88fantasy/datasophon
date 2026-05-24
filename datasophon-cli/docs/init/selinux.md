# init selinux — 关闭 SELinux

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitSelinux.java`

## 用途

在目标节点上关闭 SELinux：

1. 运行时立即关闭：`setenforce 0`
2. 持久化关闭：将 `/etc/selinux/config` 中 `SELINUX=enforcing` 改为 `SELINUX=disabled`

若 `getenforce` 返回非 `Enforcing`（已经是 `Permissive` 或 `Disabled`），直接跳过并返回成功。

---

## 参数

本命令无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

---

## 示例

```bash
java -jar datasophon-cli.jar init selinux
```

---

## 行为说明

| `getenforce` 输出 | 行为 |
|---|---|
| `Enforcing` | `setenforce 0` + 修改配置文件 |
| 其他（`Permissive`/`Disabled`/不存在） | 打印"SELINUX closed."并返回成功 |

---

## 注意事项

- 修改配置文件后立即生效（无需重启）。
- Ubuntu/Debian 一般不安装 SELinux，`getenforce` 会失败，命令返回 `false`——在 Ubuntu 环境下请确认是否真的需要运行此命令。
