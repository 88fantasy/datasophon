# init osSafeConf — OS 基线安全加固

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitOsSafeConf.java`

## 用途

按企业级安全基线对目标节点进行加固，涵盖账号、密码、SSH、网络等多个维度：

| 加固项 | 配置文件 | 具体内容 |
|---|---|---|
| 闲置超时退出 | `/etc/profile` | `TMOUT=300`（5 分钟无操作自动退出） |
| 历史命令条数 | `/etc/profile` | `HISTSIZE=3` |
| 关键文件权限 | — | `passwd(644)` / `shadow(400)` / `group(644)` / `services(644)` |
| 密码复杂度 | `/etc/pam.d/system-auth` 或 `common-password` | `pam_cracklib.so retry=3 minlen=8 dcredit=-1 ucredit=-1` |
| 密码有效期 | `/etc/login.defs` | `PASS_MAX_DAYS 90` / `PASS_MIN_DAYS 10` / `PASS_WARN_AGE 7` |
| 密码锁定策略 | `/etc/pam.d/system-auth` 或 `login` | 5 次失败锁定 180s（`pam_tally2.so deny=5`） |
| 密码复用次数 | `/etc/pam.d/system-auth` 或 `common-password` | `remember=5`（不能复用最近 5 个密码） |
| 禁用闲置账号 | `/etc/shadow` | 锁定 `lp/sync/halt/news/uucp/operator` 等 13 个账号 |
| 禁用闲置账号 shell | `/etc/passwd` | 设 shell 为 `/bin/false` |
| Hosts 访问控制 | `/etc/hosts.allow` / `/etc/hosts.deny` | `telnet: all: allow` / `telnet: all` |
| 禁止 IP 源路由 | `/etc/sysctl.conf` | `net.ipv4.conf.all.accept_source_route=0` |
| 禁止 IP 路由转发 | `/etc/sysctl.conf` | `net.ipv4.conf.all.accept_redirects=0` |
| 审计日志保留周期 | `/etc/logrotate.conf` | `rotate 50`（保留 50 周） |
| SSH Banner | `/etc/ssh/sshd_config` / `/etc/motd` | 设置登录警告 banner |
| SSH 协议版本 | `/etc/ssh/sshd_config` | `Protocol 2` |
| SSH 跳过 DNS 检查 | `/etc/ssh/sshd_config` | `UseDNS no` |

---

## 参数

本命令无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

---

## 示例

```bash
java -jar datasophon-cli.jar init osSafeConf
```

---

## 行为说明

- 每项配置修改前自动备份原始文件（备份路径：`/etc/.<filename>.bak/<filename>.bak-YYYYMMDDHHmmss`）。
- 修改失败时根据策略分两种处理：
  - `failedSignal=abort`：恢复备份并 `System.exit(1)`
  - `failedSignal=skip`：打印警告，跳过继续
- 已有同名配置行时先删除旧行再追加新行（幂等）。
- 此命令**必须以 root 身份运行**（内部调用 `whoami` 校验）。
- SSH 相关修改会触发 `systemctl restart sshd`，若 restart 失败则 `System.exit(1)`。

---

## 注意事项

- `PASS_MAX_DAYS 90` 会影响所有用户（包括 `root`），请确认与公司安全策略一致。
- `HISTSIZE=3` 限制历史命令数非常激进，线上排查问题时可能不便。
- `denyRootToSsh`（禁止 root SSH 登录）已在源码中注释掉，不会执行。
