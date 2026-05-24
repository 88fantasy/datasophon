# init system-conf — 优化系统配置

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitSystemConf.java`

## 用途

对目标节点进行操作系统参数调优，修改三个配置文件：

| 配置文件 | 修改内容 |
|---|---|
| `/etc/systemd/system.conf` | `DefaultLimitNOFILE=1024000`、`DefaultLimitNPROC=1024000` |
| `/etc/security/limits.conf` | `nofile 1048576`、`nproc unlimited` 等 8 项 |
| `/etc/sysctl.conf` | `kernel.pid_max=1000000` |

对 Ubuntu 系统额外处理：
- 确保 `/etc/rc.d/init.d/` → `/etc/init.d/` 软链接存在
- 配置并启用 `rc-local.service`

---

## 参数

本命令无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

---

## 示例

```bash
java -jar datasophon-cli.jar init system-conf
```

---

## 行为说明

- 所有修改均幂等：先删除已有同名配置行，再追加新值。
- 修改完 `sysctl.conf` 后自动执行 `sysctl -p` 加载生效。
- CentOS 7 额外向 `/etc/security/limits.conf` 写入 nproc unlimited 规则（对应旧版内核限制）。

---

## 修改后的 limits.conf 关键内容

```
*    soft    nofile    1048576
*    hard    nofile    1048576
*    soft    nproc     unlimited
*    hard    nproc     unlimited
*    soft    fsize     unlimited
*    hard    fsize     unlimited
*    soft    cpu       unlimited
*    hard    cpu       unlimited
*    soft    as        unlimited
*    hard    as        unlimited
```

---

## 注意事项

- 修改 `system.conf` 需要 systemd 重载（`systemctl daemon-reload`）后才对新启动的服务生效，本命令不自动执行重载。
- `sysctl -p` 失败不会导致命令返回失败（容错处理）。
