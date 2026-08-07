package com.datasophon.lineage;

import com.datasophon.lineage.SqlScript.TempTableInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the physical OpenLineage dataset identity for every source/sink table in a Flink
 * {@code CompiledPlan}, paired per sink rather than flattened across the whole plan.
 *
 * <p><b>Why per-sink pairing, not one flat set</b> (T15 finding, see §6 T15 row in
 * docs/data-lineage-Flink实时链路验证-实施方案-2026-08-05.md): a {@code STATEMENT SET} with N
 * independent {@code INSERT} statements compiles into N disjoint connected components in the plan
 * — flattening all of them into one input set and one output set (the original design) produces a
 * full N×M cross product when Gravitino stores the resulting OpenLineage event, most of which are
 * factually wrong edges (e.g. table A's data never actually reaches table B's sink). The plan JSON
 * carries an explicit {@code edges: [{source, target}]} array connecting node ids — walking it
 * backward from each {@code dynamicTableSink} node recovers exactly which inputs feed that specific
 * sink, with no cross-statement leakage. A job with a single INSERT (T9) degenerates to one pipeline
 * with N inputs, same as before.
 *
 * <p><b>Why this isn't a single lookup</b> (found while building T10, see P0-3/T11 evidence in the
 * same doc §6): {@code CompiledPlan.asJsonString()} gives an accurate
 * {@code `catalog`.`database`.`table`} identifier for tables that live in a real Flink catalog
 * (here: Paimon `paimon_s3`) — including flattening any wrapping {@code CREATE TEMPORARY VIEW},
 * which is exactly why the plan is used at all instead of re-parsing the SQL's FROM/JOIN clauses.
 * But for a {@code CREATE TEMPORARY TABLE} (both jobs' MySQL CDC sources, job2's Doris sink), the
 * identifier the plan reports is just "session default catalog/database + local temp name" — it
 * carries no connector/host/physical-table info at all, because the resolved table's WITH-options
 * are not serialized into the plan JSON for temporary objects. There is no config file needed to
 * plug that gap: the WITH-options are already sitting in the same SQL file this tool parses (see
 * {@link SqlScriptParser}), so temp-table identity is resolved from there instead — one source of
 * truth, not two that can drift apart.
 */
final class DatasetResolver {

  private static final Logger LOG = LoggerFactory.getLogger(DatasetResolver.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern IDENTIFIER =
      Pattern.compile("`([^`]+)`\\.`([^`]+)`\\.`([^`]+)`");

  private DatasetResolver() {}

  /** One sink and exactly the inputs reachable to it (not to any sibling sink in the plan). */
  record Pipeline(DatasetIdentity output, Set<DatasetIdentity> inputs) {}

  record Resolution(List<Pipeline> pipelines) {}

  static Resolution resolve(String compiledPlanJson, Map<String, TempTableInfo> tempTables) {
    JsonNode root;
    try {
      root = MAPPER.readTree(compiledPlanJson);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to parse CompiledPlan JSON", e);
    }

    Map<Integer, JsonNode> nodesById = new LinkedHashMap<>();
    for (JsonNode node : root.path("nodes")) {
      nodesById.put(node.path("id").asInt(), node);
    }
    Map<Integer, List<Integer>> predecessors = new LinkedHashMap<>();
    for (JsonNode edge : root.path("edges")) {
      int source = edge.path("source").asInt();
      int target = edge.path("target").asInt();
      predecessors.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
    }

    List<Pipeline> pipelines = new ArrayList<>();
    for (JsonNode node : nodesById.values()) {
      JsonNode sink = node.path("dynamicTableSink").path("table").path("identifier");
      if (!sink.isTextual()) {
        continue;
      }
      DatasetIdentity output = resolveOne(sink.asText(), tempTables, "output");
      Set<DatasetIdentity> inputs = new LinkedHashSet<>();
      Set<Integer> visited = new LinkedHashSet<>();
      Deque<Integer> pending = new ArrayDeque<>();
      pending.push(node.path("id").asInt());
      while (!pending.isEmpty()) {
        int currentId = pending.pop();
        if (!visited.add(currentId)) {
          continue;
        }
        JsonNode current = nodesById.get(currentId);
        JsonNode source = current.path("scanTableSource").path("table").path("identifier");
        if (source.isTextual()) {
          inputs.add(resolveOne(source.asText(), tempTables, "input"));
        }
        // Lookup joins (stream-exec-lookup-join) don't produce a scanTableSource node — the
        // temporal table they read is nested under temporalTable.lookupTableSource instead
        // (confirmed against a real T9 CompiledPlan JSON, see §6 T11 in the实施方案 doc).
        JsonNode lookupSource =
            current.path("temporalTable").path("lookupTableSource").path("table").path("identifier");
        if (lookupSource.isTextual()) {
          inputs.add(resolveOne(lookupSource.asText(), tempTables, "input"));
        }
        for (int predecessorId : predecessors.getOrDefault(currentId, List.of())) {
          pending.push(predecessorId);
        }
      }
      pipelines.add(new Pipeline(output, inputs));
    }
    return new Resolution(pipelines);
  }

  private static DatasetIdentity resolveOne(
      String rawIdentifier, Map<String, TempTableInfo> tempTables, String role) {
    Matcher matcher = IDENTIFIER.matcher(rawIdentifier);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Unexpected CompiledPlan table identifier shape: " + rawIdentifier);
    }
    String catalog = matcher.group(1);
    String database = matcher.group(2);
    String table = matcher.group(3);

    TempTableInfo tempTable = tempTables.get(table);
    if (tempTable != null) {
      DatasetIdentity resolved = resolveTempTable(tempTable);
      LOG.info(
          "[lineage] dataset resolved from CREATE TEMPORARY TABLE options (connector={}, role={}): {}/{}",
          tempTable.options().get("connector"),
          role,
          resolved.namespace(),
          resolved.name());
      return resolved;
    }

    // Scoped to this epic's two jobs: the only non-temporary catalog either job ever touches is
    // the Paimon `paimon_s3` catalog (see docs/lineage/ddl/paimon/01_ods_tables.sql) — not a
    // general multi-connector catalog resolver.
    DatasetIdentity resolved =
        new DatasetIdentity("paimon://" + catalog + "/" + database, table);
    LOG.info(
        "[lineage] dataset resolved from CompiledPlan catalog identifier (role={}): {}/{}",
        role,
        resolved.namespace(),
        resolved.name());
    return resolved;
  }

  private static DatasetIdentity resolveTempTable(TempTableInfo tempTable) {
    Map<String, String> options = tempTable.options();
    String connector = options.get("connector");
    if (connector == null) {
      throw new IllegalArgumentException(
          "CREATE TEMPORARY TABLE `" + tempTable.name() + "` has no 'connector' option");
    }
    return switch (connector) {
      case "mysql-cdc" ->
          new DatasetIdentity(
              "mysql-cdc://" + require(options, "hostname", tempTable) + ":" + require(options, "port", tempTable),
              require(options, "database-name", tempTable) + "." + require(options, "table-name", tempTable));
      case "doris" ->
          new DatasetIdentity(
              "doris://" + require(options, "fenodes", tempTable),
              require(options, "table.identifier", tempTable));
      default ->
          throw new IllegalArgumentException(
              "No dataset-identity rule for connector '"
                  + connector
                  + "' on CREATE TEMPORARY TABLE `"
                  + tempTable.name()
                  + "` — add one instead of guessing a generic identifier");
    };
  }

  private static String require(Map<String, String> options, String key, TempTableInfo table) {
    String value = options.get(key);
    if (value == null) {
      throw new IllegalArgumentException(
          "CREATE TEMPORARY TABLE `" + table.name() + "` is missing WITH option '" + key + "'");
    }
    return value;
  }
}
