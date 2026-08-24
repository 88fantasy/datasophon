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

Install it **only** under `plugins/`, never under `lib/`. The jar bundles the
OpenTelemetry SDK, OkHttp and Okio under their original package names, without
relocation; it is Flink's per-plugin classloader that keeps them off the user
classpath. Dropping the jar into `lib/` removes that isolation and can shadow
another component's OkHttp.

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

A Summary's `sum` is reported as `mean * count`, where the mean comes from the
histogram's bounded sample window while the count is the lifetime event count.
`sum / count` therefore still yields the window mean, but the `sum` itself is an
approximation: do not build `rate(sum)` or delta-of-sum queries on it.

Metric names use `flink.<logical-scope>.<metric-name>`. Flink scope variables
become OpenTelemetry attributes after their angle brackets are removed.

## Differences from upstream

This is not a byte-for-byte copy. The deviations below are deliberate. Keep this
table in sync when re-syncing with a newer upstream release, and re-apply items
1-5 if this module is ever replaced by the official Flink 2.x reporter --
otherwise those fixes are silently lost.

| # | Upstream | Here | Why |
| --- | --- | --- | --- |
| 1 | `close()` calls `lastResult.join(...)` unguarded | Null-checked | Upstream throws `NullPointerException` when a reporter is closed before its first `report()` |
| 2 | `report()`'s `whenComplete` callback reads the `lastResult` field | Captures the result in a local variable | By the time the callback runs, the field can already hold the next interval's result, so the log line describes the wrong batch |
| 3 | `convertHistogram` adds the max quantile twice, emitting 7 values | Emits 6: min, p50, p75, p95, p99, max | Upstream repeats `ValueAtQuantile(1.0, max)` |
| 4 | Export failures are logged without the exception | Logs the exception | Upstream discards the stack trace |
| 5 | `lastCollectTimeNanos` stays 0 until the first collection | Initialised in `open()` | Otherwise the first delta point claims a collection interval starting at 1970-01-01 |
| 6 | `close()` is not idempotent and keeps the exporter reference | Idempotent via a `closed` flag, and still keeps the reference | Flink shuts the reporter scheduler down only *after* closing reporters, so a queued `report()` must hit a closed exporter rather than a null one |
| 7 | Dispatches on `metric.getMetricType()` | Dispatches on `instanceof` | Avoids depending on the `MetricType` enum, whose constants differ across Flink versions |
| 8 | Helper classes are `public` | Package-private | They are not public API in this module |

Items 1-5 are bug fixes for defects that still exist upstream.

## Provenance

The implementation is derived from Apache Flink 2.0.2's
`flink-metrics/flink-metrics-otel` module and retains Apache license headers.
The backport keeps the Flink 2.x factory class and configuration contract so
operators can use the same reporter configuration on Flink 1.20 and 2.x.
