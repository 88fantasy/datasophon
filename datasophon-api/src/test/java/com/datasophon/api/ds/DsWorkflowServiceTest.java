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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.v2.DsDagVO;
import com.datasophon.api.dto.v2.DsPageVO;
import com.datasophon.api.dto.v2.DsTaskMetricsVO;
import com.datasophon.api.dto.v2.DsWorkflowDefinitionVO;
import com.datasophon.api.dto.v2.DsWorkflowInstanceVO;
import com.datasophon.api.service.impl.ds.DsWorkflowServiceImpl;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class DsWorkflowServiceTest {

    private final DsApiClient client = mock(DsApiClient.class);
    private final DsTaskMetricsService taskMetricsService = mock(DsTaskMetricsService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DsWorkflowServiceImpl service =
            new DsWorkflowServiceImpl(client, taskMetricsService, objectMapper, Runnable::run, Runnable::run);

    @BeforeEach
    void setUp() throws Exception {
        when(client.get(eq(7), eq("projects/99/workflow-definition"), anyMap()))
                .thenReturn(json("""
                        {"totalList":[{"code":101,"name":"batch-flow","version":3,
                        "releaseState":"ONLINE","userName":"owner","description":"synthetic",
                        "updateTime":"2026-08-25 10:20:30"}],"total":1,"pageNo":2,"pageSize":20}
                        """));
        when(client.get(eq(7), eq("projects/99/workflow-instances"), anyMap()))
                .thenReturn(json("""
                        {"totalList":[{"id":8,"workflowDefinitionCode":101,"name":"batch-flow-1",
                        "state":"SUCCESS","startTime":"2026-08-25 10:20:01",
                        "endTime":"2026-08-25 10:20:17","duration":"16s","host":"worker:1234",
                        "commandType":"START_PROCESS","dryRun":false}],"total":1}
                        """));
    }

    @Test
    void mapsPagedWorkflowsAndInstancesWithoutLeakingDsFieldNames() throws Exception {
        DsPageVO<DsWorkflowDefinitionVO> workflows = service.workflows(7, 99, 2, 20, "batch");
        DsPageVO<DsWorkflowInstanceVO> instances = service.instances(7, 99, 101, 10);

        assertThat(workflows.getList()).singleElement().satisfies(workflow -> {
            assertThat(workflow.getUpdateTime()).isEqualTo("2026-08-25T10:20:30");
            assertThat(workflow.getReleaseState()).isEqualTo("ONLINE");
        });
        assertThat(workflows.getTotal()).isEqualTo(1);
        assertThat(workflows.getPageNo()).isEqualTo(2);
        assertThat(workflows.getPageSize()).isEqualTo(20);
        assertThat(instances.getList()).singleElement().satisfies(instance -> {
            assertThat(instance.getStartTime()).isEqualTo("2026-08-25T10:20:01");
            assertThat(instance.getDurationSeconds()).isEqualTo(16);
        });
        String serialized = objectMapper.writeValueAsString(List.of(workflows, instances));
        assertThat(serialized).doesNotContain("totalList", "dagData", "workflowTaskRelationList");
    }

    @Test
    void buildsDagByTaskCodeDropsSentinelEdgeAndAcceptsNullLocations() throws Exception {
        when(client.get(eq(7), eq("projects/99/workflow-instances/8"), anyMap()))
                .thenReturn(json("""
                        {"id":8,"workflowDefinitionCode":101,"name":"batch-flow-1","state":"SUCCESS",
                        "duration":"16s","locations":null,"dagData":{"taskDefinitionList":[
                        {"code":1001,"name":"first","taskType":"SHELL"},
                        {"code":1002,"name":"second","taskType":"SPARK"}],
                        "workflowTaskRelationList":[
                        {"preTaskCode":0,"postTaskCode":1001},
                        {"preTaskCode":1001,"postTaskCode":1002}]}}
                        """));
        when(client.get(eq(7), eq("projects/99/workflow-instances/8/tasks"), anyMap()))
                .thenReturn(json("""
                        {"workflowInstanceState":"SUCCESS","taskList":[
                        {"id":12,"taskCode":1002,"state":"RUNNING_EXECUTION","taskExecuteType":"BATCH",
                        "startTime":"2026-08-25 10:20:05","duration":"12s","retryTimes":1},
                        {"id":11,"taskCode":1001,"state":"SUCCESS","taskExecuteType":"BATCH",
                        "startTime":"2026-08-25 10:20:01","endTime":"2026-08-25 10:20:04","duration":"3s"}]}
                        """));

        DsDagVO dag = service.dag(7, 99, 8);

        assertThat(dag.getEdges()).singleElement().satisfies(edge -> {
            assertThat(edge.getFrom()).isEqualTo(1001);
            assertThat(edge.getTo()).isEqualTo(1002);
        });
        assertThat(dag.getNodes()).filteredOn(node -> node.getTaskCode() == 1002).singleElement()
                .satisfies(node -> {
                    assertThat(node.getTaskInstanceId()).isEqualTo(12);
                    assertThat(node.getState()).isEqualTo("RUNNING_EXECUTION");
                    assertThat(node.getRetryTimes()).isEqualTo(1);
                });
        assertThat(dag.getLocations()).isEmpty();
    }

    @Test
    void isolatesOneNodeMetricsFailureAndKeepsOtherNodeComplete() throws Exception {
        when(client.get(eq(7), eq("projects/99/workflow-instances/8"), anyMap()))
                .thenReturn(json("""
                        {"id":8,"workflowDefinitionCode":101,"name":"mixed-flow","state":"RUNNING_EXECUTION",
                        "dagData":{"taskDefinitionList":[
                        {"code":1001,"name":"batch","taskType":"SPARK"},
                        {"code":1002,"name":"stream","taskType":"SHELL"}],
                        "workflowTaskRelationList":[{"preTaskCode":1001,"postTaskCode":1002}]}}
                        """));
        when(client.get(eq(7), eq("projects/99/workflow-instances/8/tasks"), anyMap()))
                .thenReturn(json("""
                        {"taskList":[
                        {"id":11,"taskCode":1001,"state":"SUCCESS","taskExecuteType":"BATCH"},
                        {"id":12,"taskCode":1002,"state":"RUNNING_EXECUTION","taskExecuteType":"STREAM"}]}
                        """));
        DsTaskMetricsVO batchMetrics = new DsTaskMetricsVO();
        batchMetrics.setKind("BATCH");
        when(taskMetricsService.metrics(eq(7), any())).thenAnswer(invocation -> {
            com.datasophon.api.dto.v2.DsDagNodeVO node = invocation.getArgument(1);
            if (node.getTaskInstanceId() == 12) {
                throw new IllegalStateException("synthetic Doris failure");
            }
            return batchMetrics;
        });

        DsDagVO dag = service.dag(7, 99, 8);

        assertThat(dag.getNodes()).filteredOn(node -> node.getTaskInstanceId() == 11).singleElement()
                .satisfies(node -> {
                    assertThat(node.getMetrics()).isSameAs(batchMetrics);
                    assertThat(node.getMetricsError()).isNull();
                });
        assertThat(dag.getNodes()).filteredOn(node -> node.getTaskInstanceId() == 12).singleElement()
                .satisfies(node -> {
                    assertThat(node.getFlowType()).isEqualTo("STREAM");
                    assertThat(node.getMetrics()).isNull();
                    assertThat(node.getMetricsError()).isEqualTo("LOOKUP_FAILED");
                });
    }

    @Test
    void keepsFlinkBatchTaskOnBatchMetricsPath() throws Exception {
        when(client.get(eq(7), eq("projects/99/workflow-instances/8"), anyMap()))
                .thenReturn(json("""
                        {"id":8,"workflowDefinitionCode":101,"name":"flink-batch","state":"SUCCESS",
                        "dagData":{"taskDefinitionList":[
                        {"code":1001,"name":"flink-batch","taskType":"FLINK"}],
                        "workflowTaskRelationList":[]}}
                        """));
        when(client.get(eq(7), eq("projects/99/workflow-instances/8/tasks"), anyMap()))
                .thenReturn(json("""
                        {"taskList":[
                        {"id":11,"taskCode":1001,"state":"SUCCESS","taskExecuteType":"BATCH"}]}
                        """));
        DsTaskMetricsVO metrics = new DsTaskMetricsVO();
        metrics.setKind("BATCH");
        when(taskMetricsService.metrics(eq(7), any())).thenReturn(metrics);

        DsDagVO dag = service.dag(7, 99, 8);

        assertThat(dag.getNodes()).singleElement().satisfies(node -> {
            assertThat(node.getFlowType()).isEqualTo("BATCH");
            assertThat(node.getMetrics().getKind()).isEqualTo("BATCH");
        });
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
