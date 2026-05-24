# init bin_packages — 分发 datasophon-init 资源包

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitBinPackage.java`

## 用途

将本地的 `datasophon-init` 资源包目录（含安装包、配置文件等）通过 SCP 分发到远程节点的相同路径，并在远程节点创建安装路径。

---

## 参数

独有参数 3 个（+ [InitBase 公共参数](./README.md#公共参数initbase)）：

| 短名 | 长名 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `-i` | `--datasophonInitPath` | **是** | — | 本地 `datasophon-init` 目录绝对路径，例如 `/opt/datasophon/datasophon-init` |
| `-in` | `--installPath` | **是** | — | 远程节点安装目标路径，例如 `/opt/datasophon` |
| `-pf` | `--initPathOverwriteForce` | 否 | `false` | 远程目标目录已存在时是否强制覆盖（`true`=覆盖，`false`=跳过） |

---

## 示例

```bash
# 分发资源包（跳过已存在的远程目录）
java -jar datasophon-cli.jar init bin_packages \
  -i /opt/datasophon/datasophon-init \
  -in /opt/datasophon

# 强制覆盖远程已存在的目录
java -jar datasophon-cli.jar init bin_packages \
  -i /opt/datasophon/datasophon-init \
  -in /opt/datasophon \
  -pf true
```

---

## 行为说明

1. 检查本地 `-i` 路径是否存在且为目录，不存在则 `ExecutionException`。
2. 本地安装路径不存在时创建（`mkdir -p`）。
3. 检查**远程**目标路径：
   - 已存在且 `-pf=false`：跳过 SCP，打印日志。
   - 不存在或 `-pf=true`：`mkdir -p` 远程目录，再 `sendDir` 传输整个目录。
4. 确保远程 `/opt/ddh/packages`（`MASTER_MANAGE_PACKAGE_PATH`）目录存在（本地 `mkdir -p`）。

---

## 注意事项

- 此命令在 `create cluster` 中通过 SSH 执行在**远程 Worker 节点**上——但 `sendDir` 是从本地 Master 推送到远程，因此实际调用时是"本地 Master SSH 到 Worker，再把本地目录 SCP 过去"。
- 资源包较大时传输耗时明显，日志会输出 `耗时Xs`。
- 只对**非本地节点**（IP 不等于本机 IP）的 Worker 执行，本地节点跳过。
