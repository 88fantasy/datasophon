# Valkey 8.1.8 openEuler 离线安装验证交接

> 交接日期：2026-07-18<br>
> 目标执行端：Claude Code<br>
> 仓库：`/Users/pro/.codex/worktrees/2a99/datasophon`<br>
> 验证范围：openEuler 22.03 LTS-SP3/SP4、x86_64、Datasophon 物理集群安装

## 1. 当前结论

Valkey 8.1.8 的 openEuler 原生离线包已经制作完成，并在真实的 openEuler 22.03
LTS-SP3 x86_64 节点 `ddh-02` 上完成最终 tar 包冒烟验证：

- `valkey-server`、`valkey-cli` 版本均为 8.1.8；
- 动态链接 openEuler 自带的 `libssl.so.1.1`、`libcrypto.so.1.1`，不存在 `not found`；
- 临时实例启动成功，密码认证、`SET`/`GET` 成功；
- 包内 `redis_exporter` 1.84.0 启动成功，`/metrics` 返回 `redis_up=1`；
- 本地制品与构建节点制品 SHA-256 一致。

**尚未完成的部分**：制品尚未上传 Nexus，Datasophon 元数据尚未刷新，旧失败实例尚未删除，
也尚未通过 Datasophon 正式安装链路重新安装。因此当前是“安装包完成并通过原生运行验证”，
不是“Datasophon 安装验收完成”。

## 2. 制品与校验值

制品位于：

```text
package/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz
package/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz.md5
```

校验值：

```text
SHA-256  031705868bba8060d8d6641bbaf2040e05272b752d7faefaa6ce63d1e3dc1200
MD5      4006b926af4a61399ddac50edae3e448
大小     6,806,399 bytes（约 6.5 MiB）
```

检查命令：

```bash
cd /Users/pro/.codex/worktrees/2a99/datasophon

VALKEY_ARTIFACT=package/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz
shasum -a 256 "$VALKEY_ARTIFACT"
md5 -q "$VALKEY_ARTIFACT"
tar -tzf "$VALKEY_ARTIFACT" | sed -n '1,40p'
```

包内包含：

- `bin/valkey-server`、`valkey-cli`、`valkey-benchmark` 及兼容软链；
- `redis-exporter/redis_exporter` 1.84.0，静态链接；
- `BUILD-INFO`；
- Valkey 与 redis_exporter 的许可证文件。

注意：`package/raw/packages/` 已被 `package/.gitignore` 忽略。若 Claude Code 使用的是另一个
clone/worktree，Git 不会带走这个 tar 包和 MD5 文件，必须先单独复制制品。

## 3. 本次代码和元数据改动

本次任务涉及：

```text
package/build-valkey-openeuler.sh
package/README.md
package/manifest.json
package/raw/meta/datacluster-physical/VALKEY/service_ddl.json
package/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz       # gitignored
package/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz.md5   # gitignored
docs/session-handoff-valkey-openeuler-2026-07-18.md
```

DDL 中 x86_64 制品已改为：

```text
packageName:           valkey-8.1.8-openeuler22.03-x86_64.tar.gz
decompressPackageName: valkey-8.1.8-openeuler22.03-x86_64
```

`aarch64` 条目仍然是原有的 Jammy ARM64 包，本次没有制作 openEuler aarch64 制品。
不要在 aarch64 节点上使用本次 x86_64 包。

## 4. 工作树边界

交接时工作树还存在以下早于本任务的改动，不要还原、覆盖或混入本次提交：

```text
M  datasophon-ui-v2/config/proxy.ts
M  docs/monitoring/dashboard-selection.md
?? deploy/valkey-deploy.yaml
?? docs/session-handoff-2026-07-17.md
```

其中 `deploy/valkey-deploy.yaml` 可作为本次安装清单使用，内容是把 `ValkeyMaster` 和
`ValkeyExporter` 都部署到 `ddh-02`，但它是前序会话留下的未跟踪文件，不是本次新建文件。

开始操作前先执行：

```bash
git status --short
git diff -- package/README.md package/manifest.json \
  package/raw/meta/datacluster-physical/VALKEY/service_ddl.json
```

## 5. 已知历史现场状态

2026-07-17 曾在 `ddh-02` 安装 Ubuntu Jammy 预编译包。安装阶段完成，但启动失败：

```text
/data/install_datasophon/valkey-8.1.8-jammy-x86_64/bin/valkey-server:
error while loading shared libraries: libssl.so.3: cannot open shared object file
```

当时状态：

- `ValkeyMaster` 启动失败；
- `ValkeyExporter` 因主角色失败而没有真正安装/启动；
- 节点上遗留目录 `/data/install_datasophon/valkey-8.1.8-jammy-x86_64`；
- `/data/install_datasophon/valkey` 是指向旧目录的软链；
- 业务数据目录参数默认是 `/data/valkey`，它与安装软链不是同一路径。

重新安装前不要直接删除 `/data/valkey`。即使预计旧实例没有业务数据，也必须先检查并保留。

## 6. 构建信息与节点副作用

构建脚本：

```text
package/build-valkey-openeuler.sh
```

固定输入：

```text
Valkey 8.1.8 source SHA-256:
0edc455ba7524f0cfa4f73fdc70b91dec6941e893a09bcbdd012470d08043cec

redis_exporter 1.84.0 linux-amd64 SHA-256:
f13280147f1a0f6ed5f5d61ac80620c0b64049d76a99c7a5f043319efeb368fd
```

构建在 `ddh-02` 完成。为构建，从节点的 `LOCAL-REPO` 安装了 `make` 及其依赖
`gc`、`guile`、`libtool-ltdl`；没有替换系统 OpenSSL。构建资料保留在：

```text
/data/valkey-package-build/input
/data/valkey-package-build/output
/data/valkey-package-build/build-valkey-openeuler.sh
/data/valkey-package-build/package.log
```

临时编译目录已经清理，保留内容约 15 MiB。

## 7. SP4 兼容性边界

本包在 SP3 编译和实测。针对 openEuler 22.03 LTS-SP4 已做 ELF ABI 与官方仓库核验：

|       项目       |                本包要求                |          SP4 官方 x86_64 仓库          |
|----------------|------------------------------------|------------------------------------|
| glibc          | 最高 `GLIBC_2.34`                    | `glibc-2.34-152.oe2203sp4`         |
| OpenSSL        | `libssl.so.1.1`、`libcrypto.so.1.1` | `openssl-libs-1.1.1wa-7.oe2203sp4` |
| 架构             | x86_64                             | x86_64                             |
| redis_exporter | 静态链接                               | 不依赖目标机动态库                          |

因此判断可用于 SP4 x86_64，但尚未在 SP4 实机启动验证。如果验证环境是 SP4，先执行：

```bash
uname -m
cat /etc/openEuler-release
rpm -q glibc openssl-libs zlib
```

必须确认 `uname -m` 为 `x86_64`。

参考：

- <https://docs.openeuler.org/en/docs/22.03_LTS_SP4/server/installation_upgrade/upgrade/openeuler_22.03_lts_upgrade_and_downgrade_guide.html>
- <https://repo.openeuler.org/openEuler-22.03-LTS-SP4/OS/x86_64/repodata/repomd.xml>

## 8. Claude Code 安装验证步骤

### 8.1 Gate 1：本地制品预检

```bash
cd /Users/pro/.codex/worktrees/2a99/datasophon

VALKEY_ARTIFACT=package/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz
test -s "$VALKEY_ARTIFACT"
test "$(shasum -a 256 "$VALKEY_ARTIFACT" | awk '{print $1}')" = \
  031705868bba8060d8d6641bbaf2040e05272b752d7faefaa6ce63d1e3dc1200
jq empty package/manifest.json package/raw/meta/datacluster-physical/VALKEY/service_ddl.json
bash -n package/build-valkey-openeuler.sh
```

再确认 manifest、DDL、tar 顶层目录三者一致：

```bash
jq -r '.[] | select(.service == "VALKEY" and .arch == "x86_64") |
  [.packageName, .decompressPackageName] | @tsv' package/manifest.json
jq -r '.arch.x86_64 | [.packageName, .decompressPackageName] | @tsv' \
  package/raw/meta/datacluster-physical/VALKEY/service_ddl.json
tar -tzf "$VALKEY_ARTIFACT" | sed -n '1p'
```

### 8.2 Gate 2：上传 Nexus

不要把 Nexus 密码写入文档或 Git。先 dry-run，再实际上传：

```bash
read -rsp 'Nexus password: ' NEXUS_PASSWORD
echo

datasophon-cli --dry-run upload registry \
  --productPackagesPath package \
  --webHost 192.168.10.131 \
  --webPort 8081 \
  -u admin \
  -p "$NEXUS_PASSWORD" \
  --dockerHttpPort 8083 \
  --enableRegistry

datasophon-cli upload registry \
  --productPackagesPath package \
  --webHost 192.168.10.131 \
  --webPort 8081 \
  -u admin \
  -p "$NEXUS_PASSWORD" \
  --dockerHttpPort 8083 \
  --enableRegistry

unset NEXUS_PASSWORD
```

如果当前 shell 找不到 `datasophon-cli`，先定位现有可执行文件或按
`datasophon-cli-go/CLAUDE.md` 构建；不要改命令名。

上传后必须验证真实下载 URL，而不是只看 CLI 的 success 计数：

```bash
curl -fsSI \
  http://192.168.10.131:8081/repository/raw/packages/valkey-8.1.8-openeuler22.03-x86_64.tar.gz
```

期望 HTTP 200，且 `Content-Length` 与本地文件大小一致。若匿名访问被禁用，用 Nexus
账号验证，但不要把密码写入 shell history。

### 8.3 Gate 3：刷新服务元数据

运行时路径是 `/ddh/internal/meta/refresh`，不是 `/ddh/api/internal/meta/refresh`：

```bash
curl -fsS -X POST \
  http://192.168.10.131:8080/ddh/internal/meta/refresh | jq .
```

期望：

- 顶层 `success=true`；
- `data.physicalLoaded == data.physicalTotal`；
- `data.errors` 为空；
- `data.metaStorageAvailable=true`。

再检查 API 日志，确认 VALKEY 加载的新包名是
`valkey-8.1.8-openeuler22.03-x86_64.tar.gz`，不能只相信 HTTP 200。

### 8.4 Gate 4：清理旧失败实例

1. 先在 `ddh-02` 检查真实进程、端口、安装软链和数据目录；
2. 如果 Datasophon 中仍存在 VALKEY 服务实例，优先通过前端正式“删除服务实例”流程清理；
3. 如果平台状态显示 RUNNING、但真实进程不存在，先保留证据，再处理失真状态；不要直接改库；
4. 不要删除 `/data/valkey`，除非用户明确确认数据可丢弃；
5. 删除完成后确认旧角色实例记录已清理，再发起新安装。

只读检查示例：

```bash
ssh root@192.168.10.132 '
  ps -ef | grep -E "[v]alkey-server|[r]edis_exporter" || true
  ss -lntp | grep -E ":7501|:9121" || true
  ls -ld /data/install_datasophon/valkey* 2>/dev/null || true
  du -sh /data/valkey 2>/dev/null || true
'
```

### 8.5 Gate 5：重新安装

可通过前端单服务安装，或导入已有的 `deploy/valkey-deploy.yaml`：

- `ValkeyMaster` → `ddh-02`；
- `ValkeyExporter` → `ddh-02`；
- 默认服务端口 `7501`；
- 默认 exporter 端口 `9121`；
- 数据目录默认 `/data/valkey`。

发起安装前再次核对 UI 中选中的参数，尤其是密码、数据目录和端口。不要在交接文档中记录
真实 Valkey 密码。

### 8.6 Gate 6：真实运行验收

安装结束后，在 `ddh-02` 执行。不要以 UI 显示 RUNNING 或 DAG 成功作为唯一证据。

```bash
ssh root@192.168.10.132

VALKEY_HOME=$(readlink -f /data/install_datasophon/valkey)
echo "$VALKEY_HOME"
test "$(basename "$VALKEY_HOME")" = \
  valkey-8.1.8-openeuler22.03-x86_64

"$VALKEY_HOME/bin/valkey-server" --version
"$VALKEY_HOME/bin/valkey-cli" --version

if ldd "$VALKEY_HOME/bin/valkey-server" | grep -q 'not found'; then
  echo 'FAIL: unresolved shared library'
  exit 1
fi

"$VALKEY_HOME/control_valkey.sh" status master
"$VALKEY_HOME/control_valkey.sh" status exporter

ps -ef | grep -E '[v]alkey-server|[r]edis_exporter'
ss -lntp | grep -E ':7501|:9121'
```

认证读写验证：

```bash
read -rsp 'Valkey password: ' VALKEY_PASSWORD
echo
export VALKEYCLI_AUTH="$VALKEY_PASSWORD"

VALKEY_HOME=$(readlink -f /data/install_datasophon/valkey)
"$VALKEY_HOME/bin/valkey-cli" -h 127.0.0.1 -p 7501 ping
"$VALKEY_HOME/bin/valkey-cli" -h 127.0.0.1 -p 7501 \
  set codex:install-validation openEuler
"$VALKEY_HOME/bin/valkey-cli" -h 127.0.0.1 -p 7501 \
  get codex:install-validation
"$VALKEY_HOME/bin/valkey-cli" -h 127.0.0.1 -p 7501 \
  del codex:install-validation

unset VALKEYCLI_AUTH VALKEY_PASSWORD
```

期望依次看到 `PONG`、`OK`、`openEuler`、`1`。

Exporter 验证：

```bash
curl -fsS http://127.0.0.1:9121/metrics > /tmp/valkey-exporter-metrics.txt
grep -E '^redis_up(\{[^}]*\})? 1$' /tmp/valkey-exporter-metrics.txt
grep -E '^redis_exporter_build_info' /tmp/valkey-exporter-metrics.txt
```

检查日志：

```bash
tail -n 200 /data/install_datasophon/datasophon-worker/logs/VALKEY/ValkeyMaster.log
tail -n 200 /data/install_datasophon/datasophon-worker/logs/VALKEY/ValkeyExporter.log
tail -n 200 /data/install_datasophon/valkey/logs/master.log
tail -n 200 /data/install_datasophon/valkey/logs/exporter.log
```

部分日志文件只有在角色实际执行过后才会出现；不存在时先确认角色是否真的进入执行阶段。

## 9. 验收通过标准

以下条件必须全部满足：

- [ ] 本地 SHA-256 与交接值一致；
- [ ] Nexus 新包 URL 返回 200，大小正确；
- [ ] 元数据刷新成功且 `errors=[]`；
- [ ] 旧失败实例通过正式流程清理，业务数据目录未被误删；
- [ ] `/data/install_datasophon/valkey` 指向新的 openEuler 解压目录；
- [ ] `ldd valkey-server` 无 `not found`，依赖为 OpenSSL 1.1；
- [ ] `valkey-server` 和 `redis_exporter` 真实进程存在；
- [ ] 7501、9121 真实监听；
- [ ] 认证 `PING` 和临时键 `SET`/`GET`/`DEL` 全部成功；
- [ ] exporter 返回 `redis_up=1`；
- [ ] Worker、Valkey、exporter 日志没有启动级 ERROR；
- [ ] Datasophon UI/数据库状态与真实进程一致。

如果验证节点是 SP4，还需把 SP4 的 `/etc/openEuler-release`、`rpm -q`、`ldd` 和上述冒烟
结果一并记录，才能把“ABI 兼容判断”升级为“SP4 实机验证通过”。

## 10. 回滚与禁止事项

- 安装失败时优先通过 Datasophon 停止/删除新实例，不要直接杀进程后把 UI 当作已回滚；
- 不要删除 `/data/valkey`，除非明确获得数据可删除的确认；
- 不要通过安装 OpenSSL 3 或替换系统 OpenSSL 来迁就旧 Jammy 包；
- 旧 Jammy x86_64 包在当前 SP3/SP4 环境仍然不兼容，回滚元数据到旧包不会解决问题；
- 不要把本任务之外的前端、监控文档或旧交接文件混入提交；
- 不要声称 SP4 已验证，除非确实在 SP4 节点跑完 Gate 6。

## 11. 建议最终回填内容

验证结束后，在部署记录中回填：

1. 验证节点的 openEuler 版本、架构和 RPM 版本；
2. Nexus URL、制品 SHA-256；
3. 元数据刷新统计；
4. 新安装目录和软链目标；
5. Valkey、exporter 的真实 PID/端口；
6. `PING`、读写、`redis_up=1` 的证据；
7. 是否为 SP3 或 SP4 实机验收；
8. 未解决问题及是否允许进入后续批次安装。

以上信息回填完成后，本次验证方可关闭。
