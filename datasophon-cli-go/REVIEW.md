# datasophon-cli-go 代码审核报告

**审核日期**: 2026-05-25  
**审核范围**: 55 文件 / 5864 行（深审 8 文件 + 轻扫 47 文件）  
**对照基线**: `datasophon-cli/src/main/java/com/datasophon/cli/`

---

## 摘要

| 级别 | 数量 | 描述 | 已修复 |
|---|---|---|---|
| 🔴 Critical | 2 | 凭据/敏感信息写入 INFO 日志 | ✅ C1、C2 已修复 |
| 🟡 Major | 6 | panic 语义回归、资源泄漏、逻辑 bug、测试空白 | ✅ M1~M5 已修复；M6（测试覆盖）待补 |
| 🟢 Minor | 6 | 死代码、命令注入低风险面、API 设计 | ✅ N1~N4 已修复；N5/N6 可延后 |

---

## 🔴 Critical

### C1. MySQL 临时 root 密码明文写入 INFO 日志 ✅ 已修复

- **位置**: `internal/cli/init/mysql.go:157`
- **修复**: 将日志改为 `slog.Info("临时密码已获取，开始修改密码")`，移除 `tmpPasswd` 键值对。
- **原始问题**: `mysqld --initialize` 生成的临时 root 密码被以明文写入 `log/slog` INFO 流。日志通常会落盘或被日志收集系统捕获，导致临时凭据持久化暴露。
- **Java 对照**: Java 版 `InitMysql.java` 同位置无对应 log 输出，此为 Go 版新增，不是 Java parity。

---

### C2. Kuboard 默认密码明文写入 INFO 日志 ✅ 已修复

- **位置**: `internal/cli/init/kuboard.go:92`
- **修复**: 将日志改为 `slog.Info("kuboard 安装成功，访问地址：http://ip:30080（默认账号 admin，首次登录请修改密码）")`，移除硬编码密码字符串。
- **原始问题**: Kuboard 的初始默认密码 `Kuboard123` 写入 INFO 日志。即使是默认密码，日志中出现凭据是不良实践——日志可能被 SIEM 系统采集，触发误报或泄露信息。
- **Java 对照**: `InitK8sKuboard.java` 同位置输出内容一致，属于 Java 原始问题，Go 版忠实复现。

---

## 🟡 Major

### M1. 14 处 `panic()` 产生 goroutine stack trace，替换了 Java 的干净错误信息 ✅ 已修复

- **位置**: `internal/cli/init/util.go:34,41,45,49`、`offline_server.go:69,72,81,88,91,98`、`offline_slave.go:55,65`、`mysql.go:205`、`k8s_base_services.go:226`
- **修复**: 将 `Handler` 接口签名从 `Handle() bool` 改为 `Handle() error`；所有 `doRun()` 从返回 `bool` 改为返回 `error`；全部 `panic(msg)` 替换为 `return errors.New(msg)` / `return fmt.Errorf(...)`；`DownloadFromRegistry` 由 void+panic 改为返回 `error`；`BatchExecutor.ExecBatch/InstallSoftware` 由 os.Exit 改为返回 `error`。涉及 35+ 个文件，约 300 行改动。
- **原始问题**: Java 版同位置使用 `throw new RuntimeException("msg")` ——picocli 拦截后打印单行错误消息并以 exit code 1 退出，用户只看到：
  ```
  Error: apt update fail
  ```
  Go 版用 `panic("msg")`，cobra 不拦截 panic，程序输出完整 goroutine stack trace 并崩溃。这是用户体验回归，且 `panic` 会跳过调用链上所有 `defer`，包括 `chain.go` 的 `defer client.Close()`。
- **Java 对照**: `throw RuntimeException / CommandLine.ExecutionException` → 干净退出。

---

### M2. 18 处 `os.Exit(1)` 位于 handler 方法内，跳过 `defer client.Close()` ✅ 已修复

- **位置**: `os_safe_conf.go:85,131,293,338,347`、`mysql.go:84,125,167`、`library.go:103`、`docker.go:89,121,125`、`bash.go:31,45`、`nmap.go:38`、`executor/batch.go:25,46`
- **修复**: 同 M1 — 全部替换为 `return errors.New(msg)` / `return fmt.Errorf(...)`，包含在整体 `bool → error` 签名重构中。
- **原始问题**: `os.Exit()` 和 `panic()` 一样，会跳过 `chain.go:39` 的 `defer client.Close()`。对于 `batch.go` 这类被多步复用的 executor 来说，中途 Exit 破坏了任何上层清理逻辑，且 `os.Exit()` 无法被任何 wrapper 捕获，不利于日后测试。
- **Java 对照**: 同位置 `throw new CommandLine.ExecutionException(...)` — picocli 处理后干净退出，defer 语义无影响。

---

### M3. `http.NewRequest` 错误被忽略，存在 nil pointer dereference 风险 ✅ 已修复

- **位置**: `internal/cli/init/registry.go:155`、`registry_upload.go:200`
- **修复**: 两处均改为 `req, err := http.NewRequest(...)` + `if err != nil { slog.Error(...); return false }`。
- **原始问题**: `http.NewRequest` 在 URL 无效时返回 `(nil, error)`，被 `_` 丢弃后下一行 `req.SetBasicAuth` 会 nil pointer dereference panic。
- **Java 对照**: Java `HttpUtil.createRequest()` 对 URL 有前置校验，不存在 nil 返回。

---

### M4. `os.MkdirAll` 失败被静默忽略，后续步骤会以混乱的错误失败 ✅ 已修复

- **位置**: `internal/cli/create/cluster.go:85`
- **修复**: 改为 `if mkErr := os.MkdirAll(...); mkErr != nil { return fmt.Errorf("创建安装路径失败 %s: %w", ...) }`，权限不足时立即向调用方返回带路径信息的错误。
- **原始问题**: `_ = os.MkdirAll(c.InstallPath, 0755)` 权限不足时静默失败，后续步骤会以"no such file or directory"报错，调试困难。

---

### M5. `isKubernetesReady` 在 kubectl 不可达时返回 `true`（假阳性）✅ 已修复

- **位置**: `internal/cli/init/k8s_base_services.go:217-232`
- **修复**: 在循环前增加 `!r.Success || strings.TrimSpace(r.Output) == ""` 以及 `len(statuses) == 0` 的双重守卫，任一条件满足即返回 `false`。
- **原始问题**: 若 kubectl 尚未就绪（命令失败，`r.Output=""`），`strings.Fields("")` 返回 nil，for 循环不执行，函数提前返回 `true`。5 分钟等待循环因假阳性提前跳出，后续 `kubectl create namespace` 可能失败。
- **Java 对照**: Java 版同位置逻辑相同，属于 Java bug 被忠实复现；Go 修复后优于 Java 原版。

---

### M6. 测试覆盖率极低：2 文件 / 143 行（2.4%），关键路径零测试

- **位置**: `test/` 目录
- **问题**: 当前仅有 `test/config/loader_test.go`（49 行）和 `test/executor/local_test.go`（94 行），覆盖率约 2.4%（143/5864 行）。以下高风险模块完全无测试：
  - `internal/executor/ssh.go`（202 行，SSH 执行 + sftp 文件传输）
  - `internal/handler/chain.go`（97 行，handler 失败语义、SSH 连接生命周期）
  - `internal/cli/create/cluster.go`（884 行，28 步 DAG 编排、条件分支）
  - `internal/cli/init/` 中任意一个 init 任务
- **建议补齐入口（按优先级）**:

  | 优先级 | 测试文件 | 覆盖点 |
  |---|---|---|
  | P0 | `test/executor/ssh_test.go` | 用 dockertest 起 sshd 容器，覆盖 `ExecShell/SendFile/SendDir/WriteLines` |
  | P0 | `test/handler/chain_test.go` | 失败中断但不抛的语义契约；SSH 连接生命周期 |
  | P1 | `test/cli/create/cluster_test.go` | 用 mock executor 验证 `initALL` 步骤顺序、`workerNodes` 过滤、`slavesNodesExec` 过滤 |
  | P1 | `test/cli/init/os_safe_conf_test.go` | `editConf` 备份逻辑、`failedSignal` 分支 |
  | P2 | `test/cli/init/k8s_base_services_test.go` | `isKubernetesReady` 假阳性修复回归测试 |
  | P2 | `test/cli/init/mysql_test.go` | `checkStart` panic → error 转换 |

---

## 🟢 Minor

### N1. `localExecutor` 函数定义但从未调用（死代码）✅ 已修复

- **位置**: `internal/cli/create/cluster.go`（已删除）
- **修复**: 删除 `localExecutor` 函数及对应的 `executor` import（其为唯一调用方，删除后 import 变为孤立）。
- **原始问题**: 
  ```go
  func localExecutor(dryRun bool) executor.Executor {
      return executor.NewLocalExecutor(dryRun)
  }
  ```
  `go vet` / `staticcheck` 会标记此函数为未使用死代码。

---

### N2. `splitCmd` 手写字符串分割，应使用 `strings.Fields` ✅ 已修复

- **位置**: `internal/cli/init/os_safe_conf.go`（已删除）
- **修复**: 删除 `splitCmd` 死代码包装函数（该函数已无任何调用点）。调用方（`editConf` 辅助方法）在上一轮迭代中已直接改用 `strings.Fields(cmd)`，包装层成为多余残留。
- **原始问题**: 手写了一个按空格分割字符串的循环，`strings.Fields()` 一行即可完成，且能正确处理连续空格。

---

### N3. `allhost.go` shell 命令拼接含半可信用户输入 ✅ 已修复

- **位置**: `internal/cli/init/allhost.go:35,38`
- **修复**: 将 `echo %s %s` 改为 `printf '%%s %%s\\n' '%s' '%s'`，单引号包裹参数使 shell 元字符失效。
  ```go
  exec.ExecShell(fmt.Sprintf("printf '%%s %%s\\n' '%s' '%s' >>/etc/hosts", node.IP, node.Hostname))
  ```
- **原始问题**:
  ```go
  exec.ExecShell(fmt.Sprintf("echo %s %s >>/etc/hosts", node.IP, node.Hostname))
  ```
  `node.IP` 和 `node.Hostname` 来自 `cluster.yml`，若包含空格、`&&`、`;` 等 shell 元字符，可注入任意命令。现实场景中 `cluster.yml` 由运维自己编写，可信度较高，但仍是不良实践。

---

### N4. MySQL 密码通过命令行参数传递，`ps aux` 可见 ✅ 已修复

- **位置**: `internal/cli/init/mysql.go`（`installCentos` + `rootUserConf`）
- **修复**: 将旧密码（`tmpPasswd`）和新密码（`t.Password`）分别写入 `chmod 600` 的临时 cnf 文件；SQL 语句写入临时 sql 文件；通过 `--defaults-extra-file=<cnf>` 和 stdin 重定向执行，命令行中不再出现任何密码。操作完成后删除两个临时文件。
  ```go
  // 旧密码：写入临时 cnf
  exec.WriteLines([]string{"[client]", "password=" + tmpPasswd}, "/tmp/.dsph_mysql_old.cnf")
  exec.ExecShell("chmod 600 /tmp/.dsph_mysql_old.cnf")
  // 新密码 SQL：写入临时文件
  exec.WriteLines([]string{"ALTER USER 'root'@'localhost' IDENTIFIED BY 'NEWPASS';"}, "/tmp/.dsph_mysql_init.sql")
  exec.ExecShell("mysql --defaults-extra-file=/tmp/.dsph_mysql_old.cnf -uroot < /tmp/.dsph_mysql_init.sql")
  exec.ExecShell("rm -f /tmp/.dsph_mysql_old.cnf /tmp/.dsph_mysql_init.sql")
  ```
- **原始问题**:
  ```go
  exec.ExecShell(fmt.Sprintf("/usr/bin/mysqladmin -uroot -p'%s' password '%s'", tmpPasswd, t.Password))
  exec.ExecShell(fmt.Sprintf("mysql -uroot -P'%d' -p'%s' ...", t.Port, t.Password))
  ```
  密码以明文出现在进程参数，在 MySQL 操作期间可通过 `ps aux` 被同机其他用户读取。
- **Java 对照**: Java 版同位置行为一致，属于 Java 原始问题。

---

### N5. `DownloadFromRegistry` 接受 9 个参数，建议用结构体

- **位置**: `internal/cli/init/util.go:14`
- **问题**:
  ```go
  func DownloadFromRegistry(exec executor.Executor, enableRegistry bool,
      registryIP, registryPort, registryUsername, registryPassword string,
      sourceName, distPath string, isCheckExist bool)
  ```
  9 个参数，前 6 个在每次调用时重复传入相同的 `TaskBase` 字段。Go 惯用做法是将相关参数提取为结构体。
- **建议修复**: 提取 `RegistryConfig{ Enable bool; IP, Port, User, Password string }` 参数对象，调用方只传 `*RegistryConfig`。

---

### N6. `InitK8sBaseServices.SSHPasswd` 作为 CLI flag 暴露

- **位置**: `internal/cli/init/k8s_base_services.go:80`
- **问题**:
  ```go
  cmd.Flags().StringVar(&t.SSHPasswd, "sshPasswd", "", "ssh 密码")
  ```
  通过 `--sshPasswd=xxx` 传入的密码会出现在 shell 历史记录和进程参数列表中。
- **建议修复**: 从环境变量或交互式提示读取敏感字段，而非 CLI flag。

---

## Java 行为对照矩阵

| 类/方法 | Go 对应 | 状态 | 备注 |
|---|---|---|---|
| `CreateCluster.initALL()` 28 步顺序 | `cluster.go:initALL()` | ✅ | 完全一致 |
| `CreateCluster.initSingleNode()` | `cluster.go:initSingleNode()` | ✅ | 含双次 `doInitAllHost` |
| `CreateCluster.initK8s()` 7 步 | `cluster.go:initK8s()` | ✅ | 完全一致 |
| `enableRegistry + kubernetes.enable` → `doInitDocker` 双调用 | `cluster.go:169,405` | ⚠️ | Java 原始行为，Go 忠实复现；docker 安装两次 |
| `workerNodes` 过滤（排除 localIP）| `cluster.go:workerNodes()` | ✅ | `node.IP != c.localIP` |
| `slavesNodesExec` 过滤（排除 serverNode）| `cluster.go:slavesNodesExec()` | ✅ | `node.IP == serverNode.IP` |
| handler 失败中断但不抛出 | `chain.go:45 return nil` | ✅ | 与 Java `return;` 对齐 |
| HTTP resp.Body 关闭 | `registry.go:166`、`registry_upload.go:211` | ✅ | 均有 `defer resp.Body.Close()` |
| `panic()` / `throw RuntimeException` | 14 处 `panic()` | ✅ | 已全部替换为 `return errors.New()`，cobra 打印单行错误（M1 修复） |
| `os.Exit(1)` / `throw CommandLine.ExecutionException` | 18 处 `os.Exit` | ✅ | 已全部替换为 `return errors.New()`，defer client.Close() 正常执行（M2 修复） |
| `InitMysql.checkStart` panic | `mysql.go` | ✅ | 已改为返回 `error`（M1/M2 修复） |
| `isKubernetesReady` 空输出 | `k8s_base_services.go:217` | ✅ | 增加 `!r.Success || 空输出` 守卫，消除假阳性（M5 修复） |
| `InitRegistryUpload.uploadFile` | `registry_upload.go:177` | ✅ | `defer file.Close()` 正确 |
| `SSHExecutor.SendDir` 文件逐一关闭 | `ssh.go:100-134` | ✅ | 显式 Close，无泄漏 |
| `SendFile` override=false 时跳过 | `ssh.go:57-60` | ✅ | 与 Java 语义一致 |

---

## 后续建议

### Phase 5/6 启动前应处理的项

- **🔴 C1** ✅ 已修复：移除 mysql.go 临时密码日志
- **🔴 C2** ✅ 已修复：移除 kuboard.go 硬编码密码字符串
- **🟡 M1** ✅ 已修复：`bool → error` 重构，消除全部 panic + os.Exit，恢复 defer 语义
- **🟡 M2** ✅ 已修复：同上（M1/M2 合并一次 PR 完成）
- **🟡 M3** ✅ 已修复：registry.go + registry_upload.go `http.NewRequest` nil 检查
- **🟡 M4** ✅ 已修复：cluster.go `os.MkdirAll` 错误传播
- **🟡 M5** ✅ 已修复：`isKubernetesReady` 假阳性，增加 `!r.Success || 空输出` 双重守卫

### 可延后到 Phase 5/6 之后的项

- **🟡 M6**: 补充测试套件（建议专项 sprint）
- **🟢 N1** ✅ 已修复：删除 `cluster.go` 死代码 `localExecutor` + 孤立 `executor` import
- **🟢 N2** ✅ 已修复：删除 `os_safe_conf.go` 死代码 `splitCmd` 包装函数
- **🟢 N3** ✅ 已修复：`allhost.go` 用 `printf` 单引号参数替换裸 `echo`，消除 shell 元字符注入面
- **🟢 N4** ✅ 已修复：`mysql.go` 改用临时 cnf/sql 文件传递密码，消除 `ps aux` 暴露
- **🟢 N5**: `DownloadFromRegistry` 9 参数建议提取 `RegistryConfig` 结构体（可在 Phase 5/6 重构时处理）
- **🟢 N6**: `--sshPasswd` CLI flag 建议改为环境变量或交互式输入（可在 Phase 5/6 处理）
