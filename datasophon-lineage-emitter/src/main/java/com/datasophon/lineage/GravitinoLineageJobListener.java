package com.datasophon.lineage;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.core.execution.JobListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires the START event on job submission and COMPLETE/FAIL on job termination (see the sequence
 * diagram in docs/data-lineage-Flink实时链路-技术方案-2026-08-05.md §5.1) — once per {@link
 * DatasetResolver.Pipeline}, since one physical Flink JobID (a {@code STATEMENT SET} job) can
 * compile several independent INSERT pipelines that must not share a runId (T15 finding: sharing
 * one runId across pipelines is what produced the cross-product edges this per-pipeline emission
 * replaces). All pipelines share the same physical job's START/terminal timing — they just get
 * distinct runIds, one per sink.
 *
 * <p>{@code onJobExecuted} receives a null {@link org.apache.flink.api.common.JobExecutionResult}
 * on failure (the JobID lives only on the success path), so the JobID captured in {@code
 * onJobSubmitted} — not {@code result.getJobID()} — is what ties COMPLETE/FAIL back to the same
 * runs as the earlier START events.
 */
interface LineageEventEmitter {
  void emitStart(
      UUID runId, String flinkJobIdHex, Set<DatasetIdentity> inputs, Set<DatasetIdentity> outputs);

  void emitComplete(UUID runId, Set<DatasetIdentity> inputs, Set<DatasetIdentity> outputs);

  void emitFail(UUID runId, Set<DatasetIdentity> inputs, Set<DatasetIdentity> outputs);
}

final class GravitinoLineageJobListener implements JobListener {

  private static final Logger LOG = LoggerFactory.getLogger(GravitinoLineageJobListener.class);

  private final LineageEventEmitter emitter;
  private final List<DatasetResolver.Pipeline> pipelines;
  private UUID[] runIds;
  private boolean[] startEventsEmitted;
  private boolean[] terminalEventsEmitted;

  GravitinoLineageJobListener(LineageEventEmitter emitter, List<DatasetResolver.Pipeline> pipelines) {
    this.emitter = emitter;
    this.pipelines = pipelines;
  }

  @Override
  public void onJobSubmitted(JobClient jobClient, Throwable throwable) {
    if (throwable != null || jobClient == null) {
      LOG.warn("[lineage] job submission failed, no runId to report lineage against", throwable);
      return;
    }
    emitStartForJob(jobClient.getJobID().toHexString());
  }

  synchronized void emitStartForJob(String jobIdHex) {
    UUID[] ids = new UUID[pipelines.size()];
    boolean[] starts = new boolean[pipelines.size()];
    runIds = ids;
    startEventsEmitted = starts;
    terminalEventsEmitted = new boolean[pipelines.size()];

    RuntimeException failure = null;
    for (int i = 0; i < pipelines.size(); i++) {
      DatasetResolver.Pipeline pipeline = pipelines.get(i);
      UUID id = GravitinoLineageEmitter.runIdFor(jobIdHex, pipeline.output());
      ids[i] = id;
      try {
        emitter.emitStart(id, jobIdHex, pipeline.inputs(), Set.of(pipeline.output()));
        starts[i] = true;
      } catch (RuntimeException e) {
        LOG.error("[lineage] failed to emit START for output {}", pipeline.output().name(), e);
        failure = accumulate(failure, e);
      }
    }
    if (failure != null) {
      try {
        emitTerminal(true);
      } catch (RuntimeException compensationFailure) {
        failure.addSuppressed(compensationFailure);
      }
      throw failure;
    }
  }

  @Override
  public void onJobExecuted(
      org.apache.flink.api.common.JobExecutionResult jobExecutionResult, Throwable throwable) {
    emitTerminal(throwable != null);
  }

  void emitCompleteAfterAwait() {
    emitTerminal(false);
  }

  void emitFailAfterAwait() {
    emitTerminal(true);
  }

  private synchronized void emitTerminal(boolean failed) {
    if (runIds == null || startEventsEmitted == null || terminalEventsEmitted == null) {
      LOG.warn("[lineage] job terminated without prior START events, skipping");
      return;
    }
    RuntimeException failure = null;
    for (int i = 0; i < pipelines.size(); i++) {
      if (!startEventsEmitted[i] || terminalEventsEmitted[i]) {
        continue;
      }
      DatasetResolver.Pipeline pipeline = pipelines.get(i);
      UUID id = runIds[i];
      try {
        if (failed) {
          emitter.emitFail(id, pipeline.inputs(), Set.of(pipeline.output()));
        } else {
          emitter.emitComplete(id, pipeline.inputs(), Set.of(pipeline.output()));
        }
        terminalEventsEmitted[i] = true;
      } catch (RuntimeException e) {
        LOG.error(
            "[lineage] failed to emit {} for output {}",
            failed ? "FAIL" : "COMPLETE",
            pipeline.output().name(),
            e);
        failure = accumulate(failure, e);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private static RuntimeException accumulate(RuntimeException current, RuntimeException next) {
    if (current == null) {
      return next;
    }
    current.addSuppressed(next);
    return current;
  }
}
