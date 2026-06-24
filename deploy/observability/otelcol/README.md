# otelcol-contrib v0.154.0 vendoring

Phase A1 数据面运行物。非 Maven 产物,手动 vendoring 后经 `datasophon-cli upload registry` 上传到 Nexus raw,worker 安装时下载解压。

## 发行包(linux,部署目标)

| arch | packageName | decompressPackageName | md5(实测) |
|---|---|---|---|
| x86_64 | `otelcol-contrib_0.154.0_linux_amd64.tar.gz` | `otelcol-contrib_0.154.0` | `73c543b5dc167cbddfb9a6e25442c8db` |
| aarch64 | `otelcol-contrib_0.154.0_linux_arm64.tar.gz` | `otelcol-contrib_0.154.0` | `03cc56daf082985daee6213f042d2e6f` |

下载源:`https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v0.154.0/<packageName>`

> 与 PROMTAIL 同构:tarball 解压为扁平文件(二进制 `otelcol-contrib` + LICENSE + README),
> `service_ddl.json` 设 `createDecompressDir: true`,由 datasophon 创建 `otelcol-contrib_0.154.0/` 目录并解入;
> control.sh 以 `$current_path/otelcol-contrib` 启动。

## 必含组件(已对 v0.154.0 二进制 `otelcol-contrib components` 实测确认)

- exporters:`awss3`、`doris`、`otlp`、`otlphttp`、`prometheus`
- receivers:`awss3receiver`、`filelogreceiver`、`prometheusreceiver`、`otlpreceiver`、`otlpjsonfilereceiver`
- extensions:`filestorage`
- processors:`batch`、`memory_limiter`

> 这组组件是 A1(awss3/filestorage)、A2(doris)、A3/F2 回灌(awss3receiver)、Phase C(prometheus/filelog)
> 的二进制前提,已在 vendoring 时一次性核实。

## 配置校验(A1 验收的一部分)

`datasophon-worker/src/main/resources/templates/otelcol.ftl` 的渲染产物已用真实二进制
`otelcol-contrib validate` 通过(EXIT=0)。校验过程发现并修复两处 v0.154.0 配置缺陷:
1. `service.telemetry.metrics.address` 已废弃 → 改 `readers/pull/prometheus`(host+port);
2. `file_storage` 目录不存在即拒启 → 加 `create_directory: true`。

复验命令(需 darwin/arm64 或对应平台二进制):

```bash
sed -e 's#${queueStorageDir}#/data/otelcol/storage#g' -e 's#${ip}#10.0.0.11#g' \
    -e 's#${memLimitMiB}#512#g' -e 's#${batchSize}#8192#g' -e 's#${s3Region}#us-east-1#g' \
    -e 's#${s3Bucket}#otel-bootstrap#g' -e 's#${s3Prefix}#node#g' -e 's#${s3Endpoint}#http://mw1:9040#g' \
    datasophon-worker/src/main/resources/templates/otelcol.ftl > /tmp/otelcol.yaml
otelcol-contrib validate --config /tmp/otelcol.yaml
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
