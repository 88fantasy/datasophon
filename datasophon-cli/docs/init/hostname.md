# init hostname — 设置节点 hostname

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitHostname.java`

## 用途

设置目标节点的 hostname（同时修改 `/etc/hostname`、`/etc/sysconfig/network` 和调用 `hostnamectl`），并校验设置是否生效。

---

## 参数

独有参数 1 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-h` | `--hostname` | **是** | — | 目标 hostname，例如 `app2` |

---

## 示例

```bash
# 设置本地节点 hostname 为 app2
java -jar datasophon-cli.jar init hostname -h app2
```

---

## 行为说明

执行步骤：

1. `echo <hostname> > /etc/hostname`
2. 若 `/etc/sysconfig/network` 存在：
   ```bash
   echo HOSTNAME=<hostname> > /etc/sysconfig/network
   echo NOZEROCONF=yes >> /etc/sysconfig/network
   ```
3. `hostnamectl set-hostname <hostname>`
4. `hostnamectl set-hostname --static <hostname>`
5. 执行 `hostname` 命令校验输出是否与目标一致，不一致则返回失败。

---

## 注意事项

- `/etc/sysconfig/network` 在 Ubuntu 系统上通常不存在，命令会打印 warn 但不报错。
- 在 `create cluster` 编排中，每个节点各自调用一次（传入各自的 hostname），非批量。
- 修改后需要**重新登录 SSH** 才能在终端 prompt 看到新 hostname，但 `hostname` 命令立即生效。
