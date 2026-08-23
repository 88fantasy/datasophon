# Flink 1.20 OpenTelemetry Metrics Reporter

This standalone Maven project backports the metrics half of Apache Flink's
`flink-metrics-otel` module to Flink 1.20.x. It intentionally does not backport
the Flink 2.x trace reporter SPI.

The module is not part of the Datasophon root Maven reactor. Run its build from
the repository root:

```bash
JAVA_HOME=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home \
  ./mvnw -f datasophon-flink-metrics-otel/pom.xml clean verify -s ~/.m2/setting.xml
```

Override `flink.version` to verify another 1.20 patch release:

```bash
./mvnw -f datasophon-flink-metrics-otel/pom.xml clean verify \
  -Dflink.version=1.20.4 -s ~/.m2/setting.xml
```

Install the shaded jar as a Flink plugin:

```text
$FLINK_HOME/plugins/metrics-otel/flink-metrics-otel-1.20-1.0.0-SNAPSHOT.jar
```

Configure `config.yaml` with a scalar reporter name:

```yaml
metrics:
  reporters: otel
  reporter:
    otel:
      factory.class: org.apache.flink.metrics.otel.OpenTelemetryMetricReporterFactory
      exporter.endpoint: http://otel-collector-host:4317
      service.name: flink-1.20
      service.version: "1.20.5"
      interval: 30 SECONDS
```

`metrics.reporters` must be a scalar such as `otel`, not the YAML list `[otel]`.
For legacy `flink-conf.yaml`, use the equivalent flattened `metrics.*` keys.
The reporter supports OTLP over gRPC only. `127.0.0.1:4317` is safe only when
an OpenTelemetry Collector runs on every possible JobManager and TaskManager node.

## Metric mapping

| Flink type | OpenTelemetry type |
| --- | --- |
| Counter | monotonic delta Sum |
| Gauge | Gauge; non-numeric values are skipped |
| Meter | `<name>.count` delta Sum and `<name>.rate` Gauge |
| Histogram | Summary with min, p50, p75, p95, p99, and max |

Metric names use `flink.<logical-scope>.<metric-name>`. Flink scope variables
become OpenTelemetry attributes after their angle brackets are removed.

## Provenance

The implementation is derived from Apache Flink 2.0.2's
`flink-metrics/flink-metrics-otel` module and retains Apache license headers.
The backport keeps the Flink 2.x factory class and configuration contract so
operators can use the same reporter configuration on Flink 1.20 and 2.x.
