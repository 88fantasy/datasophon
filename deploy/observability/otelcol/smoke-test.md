# OTELCOLLECTOR A1 端到端冒烟

验证 A1 数据面:合成 OTLP → file_storage 持久化队列 → awss3exporter → Rustfs(S3 bootstrap 模式),
并验证 sink 断连时落盘不丢、恢复后重放。对应设计文档验收 §5.1(S3 段)与 §5.6(落盘不丢段)。

> 前置:需可达的 Rustfs(S3,mw1:9040)+ 预建 bucket `otel-bootstrap`;运行平台对应的
> otelcol-contrib v0.154.0 二进制(见同目录 README)。

## 1. 渲染节点配置 + 启 otelcol

```bash
# 用与渲染测试相同参数生成具体配置
sed -e 's#${queueStorageDir}#/data/otelcol/storage#g' -e 's#${ip}#10.0.0.11#g' \
    -e 's#${memLimitMiB}#512#g' -e 's#${batchSize}#8192#g' -e 's#${s3Region}#us-east-1#g' \
    -e 's#${s3Bucket}#otel-bootstrap#g' -e 's#${s3Prefix}#node#g' -e 's#${s3Endpoint}#http://mw1:9040#g' \
    datasophon-worker/src/main/resources/templates/otelcol.ftl > /tmp/otelcol.yaml
otelcol-contrib validate --config /tmp/otelcol.yaml   # 期望 EXIT=0
otelcol-contrib --config /tmp/otelcol.yaml &
sleep 3
curl -s localhost:8888/metrics | grep otelcol_process_uptime   # self-metrics 在线(§5.4 地基)
```

期望:`:8888/metrics` 返回含 `otelcol_process_uptime`。

## 2. 灌合成 OTLP,验证落 Rustfs

```bash
curl -s -X POST localhost:4318/v1/traces -H 'Content-Type: application/json' \
  -d '{"resourceSpans":[{"scopeSpans":[{"spans":[{"traceId":"00000000000000000000000000000001","spanId":"0000000000000001","name":"smoke"}]}]}]}'
sleep 10
aws --endpoint-url http://mw1:9040 s3 ls s3://otel-bootstrap/ --recursive | grep node
```

期望:bucket 下出现 `node/...` 前缀对象。

## 3. 验证 file_storage 落盘不丢(§5.6)

```bash
# 把 s3Endpoint 改为不可达,重启 otelcol,再灌数据
ls -l /data/otelcol/storage                         # 持久化队列有数据文件
curl -s localhost:8888/metrics | grep -E "otelcol_exporter_queue_size|otelcol_exporter_send_failed"
# 恢复 s3Endpoint 可达,重启,确认对象最终补齐到 Rustfs
```

期望:sink 不可达期间 `/data/otelcol/storage` 有持久化文件、`queue_size>0` 且无丢弃;恢复后对象补齐。

## 验证状态(2026-06-19)

| 项 | 状态 |
|---|---|
| 配置 `validate`(真实 v0.154.0 二进制) | ✅ 已通过(EXIT=0,见 README) |
| 组件齐全(awss3/filestorage/...) | ✅ 已实测确认(见 README) |
| 合成 OTLP 落 Rustfs(步骤 1-2) | ⏳ 待真实环境(本地无 Rustfs 运行实例) |
| file_storage 落盘不丢(步骤 3) | ⏳ 待真实环境 |

> A1 的纯配置/组件验证已在开发机完成;涉及 Rustfs/网络的端到端步骤需在具备 S3 sink 的环境执行。
