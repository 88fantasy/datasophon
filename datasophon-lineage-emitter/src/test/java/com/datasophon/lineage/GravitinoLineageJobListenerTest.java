package com.datasophon.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GravitinoLineageJobListenerTest {

  @Test
  void retriesOnlyTheTerminalEventThatFailed() {
    RecordingEmitter emitter = new RecordingEmitter();
    emitter.failCompleteOnceFor = "sink_a";
    GravitinoLineageJobListener listener =
        new GravitinoLineageJobListener(emitter, List.of(pipeline("sink_a"), pipeline("sink_b")));

    listener.emitStartForJob("5a08eb018fa0ed1a47275378c0658438");

    assertThrows(IllegalStateException.class, listener::emitCompleteAfterAwait);
    assertEquals(1, emitter.completeAttempts.get("sink_a"));
    assertEquals(1, emitter.completeAttempts.get("sink_b"));

    listener.emitCompleteAfterAwait();

    assertEquals(2, emitter.completeAttempts.get("sink_a"));
    assertEquals(1, emitter.completeAttempts.get("sink_b"));
  }

  @Test
  void compensatesSuccessfulStartsWhenAnotherStartFails() {
    RecordingEmitter emitter = new RecordingEmitter();
    emitter.failStartFor = "sink_b";
    GravitinoLineageJobListener listener =
        new GravitinoLineageJobListener(
            emitter, List.of(pipeline("sink_a"), pipeline("sink_b"), pipeline("sink_c")));

    assertThrows(
        IllegalStateException.class,
        () -> listener.emitStartForJob("5a08eb018fa0ed1a47275378c0658438"));

    assertEquals(Map.of("sink_a", 1, "sink_b", 1, "sink_c", 1), emitter.startAttempts);
    assertEquals(Map.of("sink_a", 1, "sink_c", 1), emitter.failAttempts);
  }

  private static DatasetResolver.Pipeline pipeline(String outputName) {
    return new DatasetResolver.Pipeline(
        new DatasetIdentity("paimon://catalog/database", outputName), Set.of());
  }

  private static final class RecordingEmitter implements LineageEventEmitter {
    private final Map<String, Integer> startAttempts = new HashMap<>();
    private final Map<String, Integer> completeAttempts = new HashMap<>();
    private final Map<String, Integer> failAttempts = new HashMap<>();
    private String failStartFor;
    private String failCompleteOnceFor;

    @Override
    public void emitStart(
        UUID runId,
        String flinkJobIdHex,
        Set<DatasetIdentity> inputs,
        Set<DatasetIdentity> outputs) {
      String output = outputName(outputs);
      startAttempts.merge(output, 1, Integer::sum);
      if (output.equals(failStartFor)) {
        throw new IllegalStateException("start failed");
      }
    }

    @Override
    public void emitComplete(
        UUID runId, Set<DatasetIdentity> inputs, Set<DatasetIdentity> outputs) {
      String output = outputName(outputs);
      int attempt = completeAttempts.merge(output, 1, Integer::sum);
      if (output.equals(failCompleteOnceFor) && attempt == 1) {
        throw new IllegalStateException("complete failed");
      }
    }

    @Override
    public void emitFail(
        UUID runId, Set<DatasetIdentity> inputs, Set<DatasetIdentity> outputs) {
      failAttempts.merge(outputName(outputs), 1, Integer::sum);
    }

    private static String outputName(Set<DatasetIdentity> outputs) {
      return outputs.iterator().next().name();
    }
  }
}
