# init swap — 关闭 Swap 分区

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitSwap.java`

## 用途

在目标节点上永久关闭 Swap：

1. 注释掉 `/etc/fstab` 中的 swap 挂载行
2. 设置 `vm.swappiness=0`（写入 `/etc/sysctl.conf` 并 `sysctl -p` 加载）
3. 执行 `swapoff -a && swapon -a`

---

## 参数

本命令无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

---

## 示例

```bash
java -jar datasophon-cli.jar init swap
```

---

## 行为说明

- 如果 `/etc/sysctl.conf` 中已有 `vm.swappiness=xxx`，则替换为 `vm.swappiness=0`；不存在则追加。
- 任意步骤失败立即返回失败（不继续执行后续步骤）。

---

## 注意事项

- 关闭 Swap 后若内存不足，进程会被 OOM Killer 终止。请确保集群节点内存充足。
- Kubernetes 节点**必须**关闭 Swap，否则 kubelet 无法启动。
