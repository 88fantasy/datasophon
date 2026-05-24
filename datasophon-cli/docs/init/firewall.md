# init firewall — 关闭防火墙

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitFirewall.java`

## 用途

在目标节点上停止并禁用防火墙服务：
- **CentOS/OpenEuler**：`systemctl stop firewalld && systemctl disable firewalld`
- **Ubuntu/Debian**：`systemctl stop ufw && systemctl disable ufw`

防火墙已关闭时（`systemctl status` 返回非零）直接跳过，不报错。

---

## 参数

本命令无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

| 短名 | 长名 | 必填 | 说明 |
|---|---|---|---|
| — | — | — | 无额外参数 |

---

## 示例

### 本地执行

```bash
java -jar datasophon-cli.jar init firewall
```

### 开启制品库认证（通常不需要，但如有统一认证入口时）

```bash
java -jar datasophon-cli.jar init firewall \
  -enableR true \
  -rip 192.168.2.43 \
  -rport 8091 \
  -rusername admin \
  -rpassword <your-password>
```

---

## 行为说明

1. 执行 `systemctl status firewalld`（或 `ufw`），若服务不存在或已停止则直接返回成功。
2. 服务运行中：先 `stop` 再 `disable`。
3. `stop` 成功但 `disable` 失败时，返回失败并打印日志，**不抛异常**。

---

## 注意事项

- 生产环境若需保留防火墙，建议手动开放所需端口后跳过此步骤。
- 在 `create cluster` 编排中，此命令在所有节点上并行（实为串行遍历）执行。
