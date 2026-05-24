# init tar — 校验 tar 命令可用

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitTar.java`

## 用途

检验目标节点是否已安装 `tar` 命令（通过 `which tar`）。若未安装则抛出 `ExecutionException` 要求手动安装，**不自动安装**。

---

## 参数

独有参数 1 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-pp` | `--packagePath` | **是** | — | 安装包目录（此命令实际上未使用此参数，但 picocli 声明为必填） |

> **注意**：`-pp` 参数在 `InitTar.doRun()` 内部从未读取，属于历史遗留声明。实际运行只检查 `tar` 是否存在。

---

## 示例

```bash
java -jar datasophon-cli.jar init tar \
  -pp /opt/datasophon/datasophon-init/packages
```

---

## 行为说明

```
which tar 成功 → return true
which tar 失败 → ExecutionException: "tar command not found. 请手动安装"
```

---

## 注意事项

- 源码注释标注此命令"废弃"（`TODO, 默认已安装 tar，废弃`），但仍在 `create cluster initALL` 的 第 3 步中调用（对非本地 Worker 节点）。
- 现代 Linux 发行版（CentOS 7+、Ubuntu 18.04+）均预装 `tar`，正常情况下此命令总是成功。
