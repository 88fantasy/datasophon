# init library — 安装依赖库

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitLibrary.java`

## 用途

在目标节点上安装大数据平台运行所需的系统依赖库。安装列表按操作系统类型区分：

### CentOS / OpenEuler（x86_64）

| 包名 | 用途 |
|---|---|
| `libxslt-devel` | XML/XSLT 处理（x86_64 only） |
| `psmisc` | `fuser`/`killall` 等工具 |
| `perl-JSON` | Perl JSON 支持 |
| `xdg-utils` | 桌面工具（部分组件依赖） |
| `gcc-c++` | C++ 编译器 |
| `openssl-devel` | OpenSSL 开发库 |
| `libtool` | 通用编译辅助 |
| `telnet` | 网络连通性测试 |

### Ubuntu / Debian

| 包名 | 用途 |
|---|---|
| `psmisc` | `fuser`/`killall` 等工具 |
| `libpam-cracklib` | 密码复杂度 PAM 模块 |
| `policycoreutils` | SELinux 管理工具 |
| `telnet` | 网络连通性测试 |

两种系统通用的初始化操作：
- **Java Policy 修改**：向 `${JAVA_HOME}/jre/lib/security/java.policy` 添加 `MBeanTrustPermission "register"`
- **tmp 目录 PID 保护**：向 `/usr/lib/tmpfiles.d/tmp.conf` 追加 hsperfdata 排除规则
- **清理 Buffer/Cache**：`echo 1/2/3 > /proc/sys/vm/drop_caches`
- **加载 Profile**：`source /etc/profile && source /root/.bash_profile`

---

## 参数

本命令无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

---

## 示例

```bash
java -jar datasophon-cli.jar init library
```

---

## 行为说明

- 每个包安装前检查是否已安装（`rpm -qa` / `dpkg --list`），已安装则跳过。
- 安装失败会打印错误日志，但通常**不退出**（注释掉了 `System.exit(1)`），以保证其他包继续安装。
- `telnet` 安装失败是唯一会 `System.exit(1)` 的情况。

---

## 注意事项

- Java Policy 修改依赖 `$JAVA_HOME` 环境变量，请在 `init jdk8` 之后执行此命令。
- `create cluster` 编排中，`library` 在 `offlineSlave`（离线源配置）之后执行，确保 yum/apt 可用。
