# init allHost — 批量写入 /etc/hosts

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitAllHost.java`

## 用途

读取 `cluster-sample.yml` 中的节点列表（`nodes[]` 和 `addNodes[]`），将所有节点的 `ip hostname` 条目批量写入目标机器的 `/etc/hosts`：

```
#modify etc hosts start
192.168.2.213 app2
192.168.2.173 app3
...
#modify etc hosts end
```

同时在 `~/.ssh/config` 写入 `StrictHostKeyChecking no`，避免 SSH 首次连接时的确认提示。

---

## 参数

无独有参数，但**必须通过 `-c` 提供配置文件路径**（继承自 [InitBase](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填（此命令） | 说明 |
|---|---|---|---|
| `-c` | `--config` | **是** | `cluster-sample.yml` 路径 |
| `-cpwd` | `--cpassword` | yml 已加密时必须 | jasypt 解密密钥 |

---

## 示例

```bash
# 读取明文 yml
java -jar datasophon-cli.jar init allHost \
  -c /opt/datasophon/datasophon-init/config/cluster-sample.yml

# 读取加密 yml
java -jar datasophon-cli.jar init allHost \
  -c /opt/datasophon/datasophon-init/config/cluster-sample.yml \
  -cpwd <your-decrypt-key>
```

---

## 行为说明

1. 先删除 `/etc/hosts` 中 `#modify etc hosts start` 到 `#modify etc hosts end` 之间已有的条目（幂等）。
2. 追加所有 `nodes[]` + `addNodes[]` 的 `ip hostname` 对。
3. 用 `sed` 注释掉 `/etc/hosts` 中格式为 `x-x` 的行（避免数字范围行干扰 hostname 解析）。

---

## 注意事项

- `create cluster initSingleNode`（扩容模式）中，此命令同时在**原有节点**和**新增节点**上各跑一遍，保证双向 hosts 互通。
- 新增节点的 hosts 信息来自 yml 的 `addNodes[]` 字段，确保扩容前已在 yml 中添加新节点。
