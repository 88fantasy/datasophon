/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.ds;

import com.datasophon.api.dto.v2.DsDagEdgeVO;
import com.datasophon.api.dto.v2.DsDagLocationVO;
import com.datasophon.api.dto.v2.DsDagNodeVO;
import com.datasophon.api.dto.v2.DsDagVO;
import com.datasophon.api.dto.v2.DsPageVO;
import com.datasophon.api.dto.v2.DsProjectVO;
import com.datasophon.api.dto.v2.DsWorkflowDefinitionVO;
import com.datasophon.api.dto.v2.DsWorkflowInstanceVO;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Maps DolphinScheduler Open API structures onto Datasophon's stable workflow contract. */
@Service
public class DsWorkflowService {

    private static final Pattern DURATION_SECONDS = Pattern.compile("(\\d+)");

    private final DsApiClient client;
    private final DsTaskMetricsService taskMetricsService;
    private final ObjectMapper objectMapper;
    private final Executor masterExecutor;
    private final Executor dsMetricsExecutor;

    public DsWorkflowService(DsApiClient client,
                             DsTaskMetricsService taskMetricsService,
                             ObjectMapper objectMapper,
                             @Qualifier("masterExecutor") Executor masterExecutor,
                             @Qualifier("dsMetricsExecutor") Executor dsMetricsExecutor) {
        this.client = client;
        this.taskMetricsService = taskMetricsService;
        this.objectMapper = objectMapper;
        this.masterExecutor = masterExecutor;
        this.dsMetricsExecutor = dsMetricsExecutor;
    }

    public DsPageVO<DsProjectVO> projects(Integer clusterId) {
        int pageNo = 1;
        int pageSize = 200;
        JsonNode page = client.get(clusterId, "projects", Map.of("pageNo", pageNo, "pageSize", pageSize));
        List<DsProjectVO> projects = new ArrayList<>();
        for (JsonNode item : listOf(page)) {
            DsProjectVO project = new DsProjectVO();
            project.setCode(item.path("code").asLong());
            project.setName(text(item, "name"));
            project.setDescription(text(item, "description"));
            project.setOwner(text(item, "userName"));
            projects.add(project);
        }
        return new DsPageVO<>(projects, page.path("total").asLong(projects.size()), pageNo, pageSize);
    }

    public DsPageVO<DsWorkflowDefinitionVO> workflows(Integer clusterId,
                                                      long projectCode,
                                                      int pageNo,
                                                      int pageSize,
                                                      String searchVal) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("pageNo", pageNo);
        query.put("pageSize", pageSize);
        query.put("searchVal", searchVal);
        JsonNode page = client.get(clusterId,
                "projects/" + projectCode + "/workflow-definition", query);
        List<DsWorkflowDefinitionVO> workflows = new ArrayList<>();
        for (JsonNode item : listOf(page)) {
            DsWorkflowDefinitionVO workflow = new DsWorkflowDefinitionVO();
            workflow.setCode(item.path("code").asLong());
            workflow.setName(text(item, "name"));
            workflow.setVersion(item.path("version").asInt());
            workflow.setReleaseState(text(item, "releaseState"));
            workflow.setOwner(text(item, "userName"));
            workflow.setDescription(text(item, "description"));
            workflow.setUpdateTime(normalizeTime(text(item, "updateTime")));
            workflows.add(workflow);
        }
        return new DsPageVO<>(workflows, page.path("total").asLong(workflows.size()), pageNo, pageSize);
    }

    public DsPageVO<DsWorkflowInstanceVO> instances(Integer clusterId,
                                                    long projectCode,
                                                    long workflowCode,
                                                    int limit) {
        JsonNode page = client.get(clusterId,
                "projects/" + projectCode + "/workflow-instances",
                Map.of("workflowDefinitionCode", workflowCode, "pageNo", 1, "pageSize", limit));
        List<DsWorkflowInstanceVO> instances = new ArrayList<>();
        for (JsonNode item : listOf(page)) {
            instances.add(toInstance(item));
        }
        return new DsPageVO<>(instances, page.path("total").asLong(instances.size()), 1, limit);
    }

    public DsDagVO dag(Integer clusterId, long projectCode, int instanceId) {
        String resource = "projects/" + projectCode + "/workflow-instances/" + instanceId;
        CompletableFuture<JsonNode> instanceFuture = CompletableFuture.supplyAsync(
                () -> client.get(clusterId, resource, Map.of()), masterExecutor);
        CompletableFuture<JsonNode> tasksFuture = CompletableFuture.supplyAsync(
                () -> client.get(clusterId, resource + "/tasks", Map.of()), masterExecutor);
        JsonNode instance = join(instanceFuture);
        JsonNode tasks = join(tasksFuture);

        Map<Long, JsonNode> tasksByCode = new LinkedHashMap<>();
        for (JsonNode task : tasks.path("taskList")) {
            tasksByCode.put(task.path("taskCode").asLong(), task);
        }

        List<DsDagNodeVO> nodes = new ArrayList<>();
        for (JsonNode definition : instance.at("/dagData/taskDefinitionList")) {
            long taskCode = definition.path("code").asLong();
            JsonNode task = tasksByCode.get(taskCode);
            DsDagNodeVO node = new DsDagNodeVO();
            node.setTaskCode(taskCode);
            node.setName(text(definition, "name"));
            node.setTaskType(text(definition, "taskType"));
            if (task != null) {
                node.setTaskExecuteType(text(task, "taskExecuteType"));
                node.setTaskInstanceId(task.path("id").isNumber() ? task.path("id").asInt() : null);
                node.setState(text(task, "state"));
                node.setStartTime(normalizeTime(text(task, "startTime")));
                node.setEndTime(normalizeTime(text(task, "endTime")));
                node.setDurationSeconds(durationSeconds(task.path("duration")));
                node.setHost(text(task, "host"));
                node.setRetryTimes(task.path("retryTimes").asInt());
            }
            node.setFlowType(isStreamTask(node.getTaskType(), node.getTaskExecuteType())
                    ? "STREAM"
                    : "BATCH");
            nodes.add(node);
        }
        enrichMetrics(clusterId, nodes);

        List<DsDagEdgeVO> edges = new ArrayList<>();
        for (JsonNode relation : instance.at("/dagData/workflowTaskRelationList")) {
            long from = relation.path("preTaskCode").asLong();
            if (from != 0) {
                edges.add(new DsDagEdgeVO(from, relation.path("postTaskCode").asLong()));
            }
        }

        DsDagVO dag = new DsDagVO();
        dag.setInstance(toInstance(instance));
        dag.setNodes(nodes);
        dag.setEdges(edges);
        dag.setLocations(locations(instance.path("locations")));
        return dag;
    }

    private void enrichMetrics(Integer clusterId, List<DsDagNodeVO> nodes) {
        List<CompletableFuture<Void>> futures = nodes.stream()
                .filter(node -> node.getTaskInstanceId() != null)
                .map(node -> metricsFuture(clusterId, node))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private CompletableFuture<Void> metricsFuture(Integer clusterId, DsDagNodeVO node) {
        try {
            return CompletableFuture
                    .supplyAsync(() -> taskMetricsService.metrics(clusterId, node), dsMetricsExecutor)
                    .orTimeout(3, TimeUnit.SECONDS)
                    .handle((metrics, error) -> {
                        if (error == null) {
                            node.setMetrics(metrics);
                        } else {
                            Throwable cause = rootCause(error);
                            node.setMetricsError(cause instanceof DsTaskMetricsService.NotBoundException
                                    ? "NOT_BOUND"
                                    : "LOOKUP_FAILED");
                        }
                        return (Void) null;
                    });
        } catch (RuntimeException rejected) {
            node.setMetricsError("LOOKUP_FAILED");
            return CompletableFuture.completedFuture(null);
        }
    }

    private static boolean isStreamTask(String taskType, String taskExecuteType) {
        return "STREAM".equalsIgnoreCase(taskExecuteType)
                || taskType != null && taskType.toUpperCase().startsWith("FLINK");
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private List<DsDagLocationVO> locations(JsonNode raw) {
        if (raw == null || raw.isNull() || (raw.isTextual() && StringUtils.isBlank(raw.asText()))) {
            return List.of();
        }
        try {
            JsonNode array = raw.isTextual() ? objectMapper.readTree(raw.asText()) : raw;
            if (!array.isArray()) {
                return List.of();
            }
            List<DsDagLocationVO> locations = new ArrayList<>();
            for (JsonNode item : array) {
                DsDagLocationVO location = new DsDagLocationVO();
                location.setTaskCode(item.path("taskCode").asLong());
                location.setX(item.path("x").asDouble());
                location.setY(item.path("y").asDouble());
                locations.add(location);
            }
            return locations;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static DsWorkflowInstanceVO toInstance(JsonNode item) {
        DsWorkflowInstanceVO instance = new DsWorkflowInstanceVO();
        instance.setId(item.path("id").asInt());
        instance.setWorkflowCode(item.path("workflowDefinitionCode").asLong());
        instance.setName(text(item, "name"));
        instance.setState(text(item, "state"));
        instance.setStartTime(normalizeTime(text(item, "startTime")));
        instance.setEndTime(normalizeTime(text(item, "endTime")));
        instance.setDurationSeconds(durationSeconds(item.path("duration")));
        instance.setHost(text(item, "host"));
        instance.setCommandType(text(item, "commandType"));
        instance.setDryRun(item.path("dryRun").asBoolean(false));
        return instance;
    }

    private static Iterable<JsonNode> listOf(JsonNode page) {
        JsonNode list = page.path("totalList");
        return list.isArray() ? list : List.of();
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String normalizeTime(String value) {
        return value == null ? null : value.replace(' ', 'T');
    }

    private static long durationSeconds(JsonNode duration) {
        if (duration == null || duration.isNull() || duration.isMissingNode()) {
            return 0;
        }
        if (duration.isNumber()) {
            return duration.asLong();
        }
        Matcher matcher = DURATION_SECONDS.matcher(duration.asText());
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0;
    }

    private static JsonNode join(CompletableFuture<JsonNode> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }
}
