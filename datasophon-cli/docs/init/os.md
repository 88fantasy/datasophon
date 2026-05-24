# init os — 初始化 hadoop 用户/组

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitOsUser.java`

## 用途

在目标节点上创建 `hadoop` 用户组和 `hadoop` 用户：

1. 检查 `/etc/group` 是否存在 `hadoop` 组，不存在则 `groupadd hadoop`
2. 检查 `/etc/passwd` 是否存在 `hadoop` 用户，不存在则 `useradd --shell /bin/bash -g hadoop hadoop`
3. 创建 `/home/hadoop/` 目录并将 `/root/.ssh` 复制进去（SSH 免密传递）

---

## 参数

本命令无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

---

## 示例

```bash
java -jar datasophon-cli.jar init os
```

---

## 行为说明

- 用户/组已存在时跳过创建，不报错。
- SSH 密钥复制：`cp -r /root/.ssh /home/hadoop/`，再 `chown -R hadoop:hadoop /home/hadoop/.ssh/`。
- 代码中有 `TODO` 标注，SSH 密钥复制逻辑待确认是否合理——生产环境请核实 `/home/hadoop/.ssh/` 权限。

---

## 注意事项

- Datasophon Worker 及多数大数据组件（HDFS、YARN 等）以 `hadoop` 用户运行。
- 此命令需要 root 权限执行。
