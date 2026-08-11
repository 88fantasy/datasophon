package com.datasophon.lineage;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineage.InputDataset;
import io.openlineage.client.OpenLineage.OutputDataset;
import io.openlineage.client.OpenLineage.RunEvent.EventType;
import io.openlineage.client.OpenLineageClient;
import io.openlineage.client.transports.HttpTransport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends START/COMPLETE/FAIL OpenLineage run events to Gravitino's {@code POST /api/lineage}.
 *
 * <p>Auth follows the same static-token pattern SPARK3 already uses for this endpoint (see
 * deploy/deployment-standalone-doris.md §7.16 "JWT 铸造步骤"): a JWT is minted once, offline, from
 * {@code gravitino.authenticator.oauth.defaultSignKey}, and handed to this process as an opaque
 * string. This class never mints or inspects the token — it only forwards it as a Bearer header,
 * same as {@code spark.openlineage.transport.auth.apiKey} does for Spark.
 */
final class GravitinoLineageEmitter implements AutoCloseable, LineageEventEmitter {

  private static final Logger LOG = LoggerFactory.getLogger(GravitinoLineageEmitter.class);
  private static final URI PRODUCER =
      URI.create("https://github.com/88fantasy/datasophon/tree/main/datasophon-lineage-emitter");
  private static final String LINEAGE_ENDPOINT_PATH = "/api/lineage";

  private final OpenLineage openLineage = new OpenLineage(PRODUCER);
  private final OpenLineageClient client;
  private final String jobNamespace;
  private final String jobName;

  GravitinoLineageEmitter(
      String gravitinoBaseUrl, String authToken, String jobNamespace, String jobName) {
    this.jobNamespace = jobNamespace;
    this.jobName = jobName;
    HttpTransport.Builder builder =
        HttpTransport.builder().uri(URI.create(gravitinoBaseUrl)).apiKey(authToken);
    builder.setEndpoint(LINEAGE_ENDPOINT_PATH);
    this.client = OpenLineageClient.builder().transport(builder.build()).build();
  }

  /**
   * Deterministic OpenLineage runId for one (Flink JobID, sink dataset) pair — stable across a
   * pipeline's START/COMPLETE/FAIL. Keyed by the output too (not just the JobID) because a single
   * {@code STATEMENT SET} job can compile N independent INSERT pipelines into one physical JobID;
   * each pipeline gets its own runId so Gravitino records its true inputs, not a cross product with
   * its sibling pipelines' inputs (see {@link DatasetResolver.Pipeline}, T15 finding). A job with a
   * single INSERT (one sink) still gets exactly one runId, same as before this key changed.
   */
  static UUID runIdFor(String flinkJobIdHex, DatasetIdentity output) {
    String key = "flink-job:" + flinkJobIdHex + ":" + output.namespace() + "/" + output.name();
    return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * T16: also stamps the {@code spark_properties.properties["spark.app.id"]} run facet with the
   * real Flink JobID. This is the exact JSON path Gravitino's {@code JdbcLineageStorage
   * .runningAppId(...)} already parses (see docs/data-lineage-任务级流速可视化-实施方案-2026-08-04.md
   * §3.1) — reusing the receiving side's existing extraction instead of touching the gravitino fork
   * ("发射端迁就接收端", per the same doc's §4.3 principle). The name says "spark" but the field is
   * engine-agnostic on the wire; only START carries it, so the L3 graph's {@code runningAppId} goes
   * back to {@code null} the moment COMPLETE/FAIL is emitted — the frontend job-metrics poller keys
   * off exactly that transition.
   */
  @Override
  public void emitStart(
      UUID runId, String flinkJobIdHex, Set<DatasetIdentity> inputs, Set<DatasetIdentity> outputs) {
    OpenLineage.DefaultRunFacet appIdFacet = new OpenLineage.DefaultRunFacet(PRODUCER);
    appIdFacet.getAdditionalProperties().put("properties", Map.of("spark.app.id", flinkJobIdHex));
    OpenLineage.RunFacets facets = openLineage.newRunFacetsBuilder().put("spark_properties", appIdFacet).build();
    emit(EventType.START, runId, facets, inputs, outputs);
  }

  @Override
  public void emitComplete(UUID runId, Set<DatasetIdentity> inputs, Set<DatasetIdentity> outputs) {
    emit(EventType.COMPLETE, runId, null, inputs, outputs);
  }

  @Override
  public void emitFail(UUID runId, Set<DatasetIdentity> inputs, Set<DatasetIdentity> outputs) {
    emit(EventType.FAIL, runId, null, inputs, outputs);
  }

  private void emit(
      EventType eventType,
      UUID runId,
      OpenLineage.RunFacets facets,
      Set<DatasetIdentity> inputs,
      Set<DatasetIdentity> outputs) {
    OpenLineage.RunBuilder runBuilder = openLineage.newRunBuilder().runId(runId);
    if (facets != null) {
      runBuilder.facets(facets);
    }
    OpenLineage.Run run = runBuilder.build();
    OpenLineage.Job job = openLineage.newJobBuilder().namespace(jobNamespace).name(jobName).build();
    List<InputDataset> inputDatasets = inputs.stream().map(this::toInput).toList();
    List<OutputDataset> outputDatasets = outputs.stream().map(this::toOutput).toList();
    OpenLineage.RunEvent event =
        openLineage
            .newRunEventBuilder()
            .eventType(eventType)
            .eventTime(ZonedDateTime.now(ZoneOffset.UTC))
            .run(run)
            .job(job)
            .inputs(inputDatasets)
            .outputs(outputDatasets)
            .build();
    LOG.info(
        "[lineage] emitting {} run={} job={}.{} inputs={} outputs={}",
        eventType,
        runId,
        jobNamespace,
        jobName,
        inputDatasets.size(),
        outputDatasets.size());
    client.emit(event);
  }

  private InputDataset toInput(DatasetIdentity dataset) {
    return openLineage.newInputDatasetBuilder().namespace(dataset.namespace()).name(dataset.name()).build();
  }

  private OutputDataset toOutput(DatasetIdentity dataset) {
    return openLineage.newOutputDatasetBuilder().namespace(dataset.namespace()).name(dataset.name()).build();
  }

  @Override
  public void close() throws Exception {
    client.close();
  }
}
