# otelcol-contrib v0.156.0 vendoring

Phase A1 数据面运行物。非 Maven 产物,手动 vendoring 后经 `datasophon-cli upload registry` 上传到 Nexus raw,worker 安装时下载解压。

> **版本升级记录(2026-07-12)**:从 v0.154.0 升级到 v0.156.0。升级前逐字节对比了 dorisexporter 的全部 12 个 SQL schema 源文件(`sql/*.sql`),v0.154.0 与 v0.156.0 完全一致,`datasophon-api/src/main/resources/observability/doris/` 下的 vendored SQL 无需改动。已用真实 v0.156.0 二进制重新执行下方"配置校验",doris/s3 两种 exporter 模式均 EXIT=0。

## 发行包(linux,部署目标)

| arch | packageName | decompressPackageName | md5(实测) |
|---|---|---|---|
| x86_64 | `otelcol-contrib_0.156.0_linux_amd64.tar.gz` | `otelcol-contrib_0.156.0` | `55e295c11826346e8915d93fb519c351` |
| aarch64 | `otelcol-contrib_0.156.0_linux_arm64.tar.gz` | `otelcol-contrib_0.156.0` | `79bf393164664c96376ee6db39a8ac00` |

下载源:`https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v0.156.0/<packageName>`

> 与 PROMTAIL 同构:tarball 解压为扁平文件(二进制 `otelcol-contrib` + LICENSE + README),
> `service_ddl.json` 设 `createDecompressDir: true`,由 datasophon 创建 `otelcol-contrib_0.156.0/` 目录并解入;
> control.sh 以 `$current_path/otelcol-contrib` 启动。

## 必含组件(已对 v0.156.0 二进制 `otelcol-contrib components` 实测确认)

- exporters:`awss3`、`doris`、`otlp`、`otlphttp`、`prometheus`
- receivers:`awss3receiver`、`filelogreceiver`、`prometheusreceiver`、`otlpreceiver`、`otlpjsonfilereceiver`
- extensions:`filestorage`
- processors:`batch`、`memory_limiter`

> 这组组件是 A1(awss3/filestorage)、A2(doris)、A3/F2 回灌(awss3receiver)、Phase C(prometheus/filelog)
> 的二进制前提,已在 vendoring 时一次性核实;升级到 v0.156.0 时已用 `otelcol-contrib components` 复核,组件名和数量均未变化。

## 配置校验(A1 验收的一部分)

`datasophon-worker/src/main/resources/templates/otelcol.ftl` 的渲染产物已用真实二进制
`otelcol-contrib validate` 通过(EXIT=0)。首次校验(v0.154.0)时发现并修复两处配置缺陷,复核确认 v0.156.0 下仍然必需:
1. `service.telemetry.metrics.address` 已废弃 → 改 `readers/pull/prometheus`(host+port);
2. `file_storage` 目录不存在即拒启 → 加 `create_directory: true`。

模板已含 `rawYaml` 覆盖分支和 `exporterMode`(`s3`/`doris`)条件分支,纯 `sed` 替换无法正确渲染 `<#if>` 指令;复验须用 Freemarker 引擎实际渲染,例如:

```bash
# 用真实 Freemarker 渲染(而非 sed),避免 <#if rawYaml>/<#if exporterMode> 等指令被当作字面文本
# 可复用 datasophon-worker/src/test/java/com/datasophon/worker/test/OtelcolTemplateTest.java 中的
# render(exporterMode, localScrapeJobsYaml) 方法逻辑,分别渲染 "doris" 和 "s3" 两种模式后:
otelcol-contrib validate --config otelcol-doris.yaml
otelcol-contrib validate --config otelcol-s3.yaml
```

## Java Agent traces 接入(Phase D)

`datasophon-api` 和 `datasophon-worker` 的发布包内置 `otel/opentelemetry-javaagent.jar`,但默认不启用。只有显式设置:

```bash
export OTEL_JAVAAGENT_ENABLED=true
```

启动脚本才会追加 `-javaagent:$DDH_HOME/otel/opentelemetry-javaagent.jar`,并默认导出:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
OTEL_EXPORTER_OTLP_PROTOCOL=grpc
OTEL_TRACES_EXPORTER=otlp
OTEL_METRICS_EXPORTER=none
OTEL_LOGS_EXPORTER=none
```

启用前置条件:本节点必须已安装并启动 OTELCOLLECTOR 角色,且 `otlp` receiver 正在监听 `localhost:4317`。未部署 collector 的节点不要开启,否则 agent 会持续尝试连接本机 OTLP 端口并产生错误日志。

可覆盖变量:

- `OTEL_SERVICE_NAME`:默认 `datasophon-api` / `datasophon-worker`。
- `OTEL_EXPORTER_OTLP_ENDPOINT`:默认 `http://localhost:4317`。
- `OTEL_EXPORTER_OTLP_PROTOCOL`:默认 `grpc`。

建议在节点 `/etc/profile` 或 systemd unit 的 `Environment=` 中注入开关与覆盖变量;启动脚本会 `source /etc/profile`。

## 上传

见 CLAUDE.local.md「upload registry 完整命令」,raw 仓库。两架构 tar.gz 放入
`{datasophonPath}/package/`,`--enableRegistry` 必须显式传入。
