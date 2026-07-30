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

package com.datasophon.api.lineage.event;

import com.datasophon.api.lineage.DatasetIdentity;
import com.datasophon.api.lineage.DecodedLineageEvent;
import com.datasophon.api.lineage.LineageEventDecoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

/** Minimal OpenLineage RunEvent decoder. No OpenLineage-specific model escapes this package. */
public final class OpenLineageEventDecoder implements LineageEventDecoder {

    @Override
    public DecodedLineageEvent decode(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("lineage payload must be a JSON object");
        }
        JsonNode job = payload.path("job");
        String engine = optionalText(payload, "engine");
        if (engine == null) {
            engine = optionalText(job.path("facets").path("jobType"), "integration");
        }
        if (engine == null) {
            engine = "UNKNOWN";
        }
        String flowType = optionalText(job.path("facets").path("jobType"), "processingType");
        if (flowType == null) {
            flowType = "UNKNOWN";
        }

        return new DecodedLineageEvent(
                requiredText(payload, "producer"),
                requiredText(payload.path("run"), "runId"),
                requiredText(payload, "eventType"),
                requiredText(job, "name"),
                engine.toLowerCase(Locale.ROOT),
                flowType.toUpperCase(Locale.ROOT),
                optionalText(payload.path("run").path("facets").path("nominalTime"), "nominalStartTime"),
                optionalText(payload, "eventTime"),
                optionalText(job.path("facets").path("sql"), "query"),
                datasets(payload.path("inputs")),
                datasets(payload.path("outputs")));
    }

    private static List<DatasetIdentity> datasets(JsonNode datasets) {
        if (datasets.isMissingNode() || datasets.isNull()) {
            return List.of();
        }
        if (!datasets.isArray()) {
            throw new IllegalArgumentException("inputs and outputs must be arrays");
        }
        List<DatasetIdentity> result = new ArrayList<>();
        for (JsonNode dataset : datasets) {
            result.add(new DatasetIdentity(requiredText(dataset, "namespace"), requiredText(dataset, "name")));
        }
        return result;
    }

    private static String requiredText(JsonNode parent, String field) {
        String value = optionalText(parent, field);
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String optionalText(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            return null;
        }
        return value.textValue();
    }
}
