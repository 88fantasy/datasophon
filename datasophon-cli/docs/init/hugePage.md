# init hugePage — 关闭透明大页

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitHugePage.java`

## 用途

关闭 Linux 透明大页（Transparent HugePage），避免影响大数据组件（如 Redis、Elasticsearch）的内存性能：

1. 立即关闭：
   ```bash
   echo never > /sys/kernel/mm/transparent_hugepage/enabled
   echo never > /sys/kernel/mm/transparent_hugepage/defrag
   ```
2. 持久化（写入 rc.local 保证重启后生效）：
   - CentOS/OpenEuler：追加到 `/etc/rc.d/rc.local`
   - Ubuntu：追加到 `/etc/rc.local`

---

## 参数

本命令无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

---

## 示例

```bash
java -jar datasophon-cli.jar init hugePage
```

---

## 行为说明

- 命令检查 rc.local 文件中是否已有 `echo never > /sys/kernel/mm/transparent_hugepage/defrag`，若存在则跳过追加（幂等）。
- rc.local 文件不存在时抛出运行时异常并退出。

---

## 注意事项

- `create cluster` 中此步骤在所有节点的**最后**执行（`initALL` 第 28 步）。
- Ubuntu 22.04 LTS 上 rc.local 默认不存在，需先执行 `init system-conf`（它会创建并启用 rc-local.service）再运行此命令。
