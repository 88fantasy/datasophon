# datasophon-cli-go 代码审核报告

**审核日期**: 2026-05-25  
**审核范围**: 55 文件 / 5864 行（深审 8 文件 + 轻扫 47 文件）  
**对照基线**: `datasophon-cli/src/main/java/com/datasophon/cli/`

---

## 摘要

| 级别 | 数量 | 描述 |
|---|---|---|
| 🔴 Critical | 2 | 凭据/敏感信息写入 INFO 日志 |
| 🟡 Major | 6 | panic 语义回归、资源泄漏、逻辑 bug、测试空白 |
| 🟢 Minor | 6 | 死代码、命令注入低风险面、API 设计 |

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

### M1. 14 处 `panic()` 产生 goroutine stack trace，替换了 Java 的干净错误信息

- **位置**: `internal/cli/init/util.go:34,41,45,49`、`offline_server.go:69,72,81,88,91,98`、`offline_slave.go:55,65`、`mysql.go:205`、`k8s_base_services.go:226`
- **问题**: Java 版同位置使用 `throw new RuntimeException("msg")` ——picocli 拦截后打印单行错误消息并以 exit code 1 退出，用户只看到：
  ```
  Error: apt update fail
  ```
  Go 版用 `panic("msg")`，cobra 不拦截 panic，程序输出完整 goroutine stack trace 并崩溃：
  ```
  goroutine 1 [running]:
  main.main()
      ...
  panic: apt update 失败
  ```
  这是用户体验回归。此外，`panic` 会跳过调用链上所有 `defer`，包括 `chain.go:39` 的 `defer client.Close()`，导致 SSH 连接未关闭（进程退出后 OS 会回收，但 defer 语义被破坏）。
- **Java 对照**: `throw RuntimeException / CommandLine.ExecutionException` → 干净退出。
- **建议修复**: 将 `doRun()` 的签名从 `bool` 改为 `error`，把 `panic(msg)` 替换为 `return errors.New(msg)`，让 `Handle()` → `chain.Handle()` 逐级传递错误，最终由 cobra `RunE` 以 `return err` 返回。这样 cobra 会打印 `Error: <msg>` 并以 exit code 1 退出，与 Java 行为对齐。

---

### M2. 18 处 `os.Exit(1)` 位于 handler 方法内，跳过 `defer client.Close()`

- **位置**: `os_safe_conf.go:85,131,293,338,347`、`mysql.go:84,125,167`、`library.go:103`、`docker.go:89,121,125`、`bash.go:31,45`、`nmap.go:38`、`executor/batch.go:25,46`
- **问题**: `os.Exit()` 和 `panic()` 一样，会跳过 `chain.go:39` 的 `defer client.Close()`。虽然进程退出后 OS 会回收连接，但对于 `batch.go` 这类被多步复用的 executor 来说，中途 Exit 破坏了任何上层清理逻辑。此外，`os.Exit()` 无法被任何 wrapper 捕获，不利于日后的测试（无法在测试中 mock 退出行为）。
- **Java 对照**: 同位置 `throw new CommandLine.ExecutionException(...)` — picocli 处理后干净退出，defer 语义无影响。
- **建议修复**: 同 M1 — 让 `doRun` 返回 `error`，替换 `os.Exit(1)` 为 `return errors.New(msg)`，在最顶层（`chain.Handle()` 或 `RunE`）统一处理退出。

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

### M5. `isKubernetesReady` 在 kubectl 不可达时返回 `true`（假阳性）

- **位置**: `internal/cli/init/k8s_base_services.go:204-212`
- **问题**:
  ```go
  func (t *InitK8sBaseServices) isKubernetesReady(exec executor.Executor) bool {
      r := exec.ExecShell("/usr/bin/kubectl get nodes | ...")
      for _, status := range strings.Fields(r.Output) { // r.Output="" 时 → nil slice
          if strings.TrimSpace(status) != "Ready" {
              return false
          }
      }
      return true  // ← 空输出时立即返回 true
  }
  ```
  若 kubectl 尚未就绪（命令失败，`r.Output=""`），`strings.Fields("")` 返回 nil，for 循环不执行，函数返回 `true`。5 分钟的等待循环会在 kubernetes 真正就绪之前提前跳出，后续 `kubectl create namespace` 可能失败。
- **Java 对照**: Java 版同位置逻辑相同，属于 Java bug 被忠实复现。
- **建议修复**: 增加命令成功检查 + 最少节点数检查：
  ```go
  if !r.Success || r.Output == "" {
      return false
  }
  statuses := strings.Fields(r.Output)
  if len(statuses) == 0 {
      return false
  }
  ```

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

### N1. `localExecutor` 函数定义但从未调用（死代码）

- **位置**: `internal/cli/create/cluster.go:882-884`
- **问题**: 
  ```go
  func localExecutor(dryRun bool) executor.Executor {
      return executor.NewLocalExecutor(dryRun)
  }
  ```
  `go vet` / `staticcheck` 会标记此函数为未使用死代码。
- **建议修复**: 直接删除。

---

### N2. `splitCmd` 手写字符串分割，应使用 `strings.Fields`

- **位置**: `internal/cli/init/os_safe_conf.go:352-368`
- **问题**: 手写了一个按空格分割字符串的循环，`strings.Fields()` 一行即可完成，且能正确处理连续空格。
- **建议修复**: `parts := strings.Fields(cmd)`

---

### N3. `allhost.go` shell 命令拼接含半可信用户输入

- **位置**: `internal/cli/init/allhost.go:35,38`
- **问题**:
  ```go
  exec.ExecShell(fmt.Sprintf("echo %s %s >>/etc/hosts", node.IP, node.Hostname))
  ```
  `node.IP` 和 `node.Hostname` 来自 `cluster.yml`，若包含空格、`&&`、`;` 等 shell 元字符，可注入任意命令。现实场景中 `cluster.yml` 由运维自己编写，可信度较高，但仍是不良实践。
- **建议修复**: 使用 `printf` 并单引号包裹，或用 `WriteFromStream`/`WriteLines` 直接写文件代替 shell echo：
  ```go
  exec.ExecShell(fmt.Sprintf("printf '%%s %%s\\n' '%s' '%s' >>/etc/hosts", node.IP, node.Hostname))
  ```

---

### N4. MySQL 密码通过命令行参数传递，`ps aux` 可见

- **位置**: `internal/cli/init/mysql.go:158,183-187`
- **问题**:
  ```go
  exec.ExecShell(fmt.Sprintf("/usr/bin/mysqladmin -uroot -p'%s' password '%s'", tmpPasswd, t.Password))
  exec.ExecShell(fmt.Sprintf("mysql -uroot -P'%d' -p'%s' ...", t.Port, t.Password))
  ```
  密码以明文出现在进程参数，在 MySQL 操作期间可通过 `ps aux` 被同机其他用户读取。
- **Java 对照**: Java 版同位置行为一致，属于 Java 原始问题。Go 重写本有机会改善：可使用 `--defaults-extra-file=<(printf '[client]\npassword=%s\n' "$PASS")` 或通过 stdin 传递密码。
- **优先级**: 低（运维环境通常受控，且需改动较大）

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
| `panic()` / `throw RuntimeException` | 14 处 `panic()` | ⚠️ | Java 通过 picocli 拦截输出干净错误；Go 产生 stack trace（M1） |
| `os.Exit(1)` / `throw CommandLine.ExecutionException` | 18 处 `os.Exit` | ⚠️ | 均中止进程，但 Go 跳过 defer（M2） |
| `InitMysql.checkStart` panic | `mysql.go:205` | ⚠️ | Java 同位置也 `throw RuntimeException`，语义一致，UX 不同 |
| `isKubernetesReady` 空输出 | `k8s_base_services.go:204` | ⚠️ | Java 同位置同逻辑，属于 Java bug 复现（M5） |
| `InitRegistryUpload.uploadFile` | `registry_upload.go:177` | ✅ | `defer file.Close()` 正确 |
| `SSHExecutor.SendDir` 文件逐一关闭 | `ssh.go:100-134` | ✅ | 显式 Close，无泄漏 |
| `SendFile` override=false 时跳过 | `ssh.go:57-60` | ✅ | 与 Java 语义一致 |

---

## 后续建议

### Phase 5/6 启动前应处理的项

- **🔴 C1** ✅ 已修复：移除 mysql.go 临时密码日志
- **🔴 C2** ✅ 已修复：移除 kuboard.go 硬编码密码字符串
- **🟡 M3** ✅ 已修复：registry.go + registry_upload.go `http.NewRequest` nil 检查
- **🟡 M4** ✅ 已修复：cluster.go `os.MkdirAll` 错误传播

### 可延后到 Phase 5/6 之后的项

- **🟡 M1/M2**: panic + os.Exit 重构需要修改所有 `doRun` 签名，涉及面广，建议单独 PR（改动约 300 行）
- **🟡 M5**: `isKubernetesReady` 假阳性修复（5 分钟，但需 k8s 环境验证）
- **🟡 M6**: 补充测试套件（建议专项 sprint）
- **🟢 N1-N6**: 所有 Minor 项可延后或在代码清理 PR 中合并处理
