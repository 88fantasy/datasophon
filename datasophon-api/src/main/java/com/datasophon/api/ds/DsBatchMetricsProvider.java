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

import com.datasophon.api.dto.v2.DsBatchOutputVO;
import com.datasophon.api.dto.v2.DsTaskMetricsVO;
import com.datasophon.api.lineage.proxy.GravitinoLineageClient;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

/** Reads batch output statistics from the run-level lineage binding. */
@Component
public class DsBatchMetricsProvider {

    private final GravitinoLineageClient lineageClient;

    public DsBatchMetricsProvider(GravitinoLineageClient lineageClient) {
        this.lineageClient = lineageClient;
    }

    public DsTaskMetricsVO metrics(Integer clusterId, int taskInstanceId) {
        JsonNode summary;
        try {
            summary = lineageClient.getRunByExternalKey(clusterId, externalKey(clusterId, taskInstanceId));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 404) {
                throw new DsTaskMetricsService.NotBoundException();
            }
            throw e;
        }
        List<DsBatchOutputVO> outputs = new ArrayList<>();
        for (JsonNode item : summary.path("outputs")) {
            DsBatchOutputVO output = new DsBatchOutputVO();
            output.setNamespace(text(item, "namespace"));
            output.setName(text(item, "name"));
            output.setRowCount(nullableLong(item.path("rowCount")));
            output.setSize(nullableLong(item.path("size")));
            output.setJobName(text(item, "jobName"));
            outputs.add(output);
        }
        DsTaskMetricsVO metrics = new DsTaskMetricsVO();
        metrics.setKind("BATCH");
        metrics.setRunCount(nullableLong(summary.path("runCount")));
        metrics.setOutputs(outputs);
        return metrics;
    }

    static String externalKey(Integer clusterId, int taskInstanceId) {
        return "ds-" + clusterId + "-" + taskInstanceId;
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Long nullableLong(JsonNode value) {
        return value != null && value.isNumber() ? value.asLong() : null;
    }
}
