# init bash — 设置 bash 解析器

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitBash.java`

## 用途

确保目标节点的默认 shell 解析器为 `bash`：

1. 检查 `/bin/sh` 软链接指向是否为 `bash`，若不是则 `sudo ln -svf bash /bin/sh`
2. 若当前用户为 `root`，将 `/etc/passwd` 中 root 的 shell 从 `/bin/sh` 改为 `/bin/bash`
3. 校验 `$SHELL` 是否等于 `/bin/bash`，否则退出（`System.exit(1)`）

---

## 参数

本命令无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

---

## 示例

```bash
java -jar datasophon-cli.jar init bash
```

---

## 行为说明

- `/bin/sh → bash` 修改为软链接操作，原有 `/bin/sh` 不删除。
- 最终校验 `$SHELL` 不通过时立即退出，防止后续步骤在错误 shell 环境下运行。

---

## 注意事项

- 在 `create cluster` 编排中，`bash` 是继 `bin_packages` / `tar` 之后较早执行的步骤（第 2 步），确保后续所有 shell 脚本在 bash 中运行。
- 部分 Linux 发行版（如 Ubuntu）`/bin/sh` 默认指向 `dash`，此命令会将其修改为 `bash`，可能影响依赖 dash 特性的脚本。
