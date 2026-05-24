# init nmap — 安装 nmap

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitNmap.java`

## 用途

在目标节点上安装 `nmap` 网络扫描工具：

- CentOS/OpenEuler：`yum install nmap -y`
- Ubuntu/Debian：`apt install nmap -y`

安装前检查是否已安装，若已安装则跳过。

---

## 参数

本命令无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

---

## 示例

```bash
java -jar datasophon-cli.jar init nmap
```

---

## 行为说明

- 安装失败时打印日志并 `System.exit(1)`（强制退出）。
- 需要离线源或 Nexus yum/apt 仓库可用。

---

## 注意事项

- 在 `create cluster initALL` 编排中，`nmap` 只安装在 `global.nmapServer.node` 指定的单个节点上（非全部节点），用于集群内网络探测。
- 单独运行此命令时在本地机器安装 nmap。
