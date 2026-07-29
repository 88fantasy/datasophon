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

package com.datasophon.api.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

class LineageGraphSnapshotTest {

    private static final NodeMeta ORDERS =
            new NodeMeta(1, "paimon", "prod", "dwd", "orders", "paimon://prod/dwd/orders", "DWD");

    @Test
    void supportsSelfLoopsAndAggregatedParallelJobsWithoutExposingMutation() {
        EdgeValue parallelJobs = new EdgeValue(List.of(
                new JobRef(1, 101, 1, "BATCH"),
                new JobRef(2, 102, 3, "STREAM")));
        MutableValueGraph<Long, EdgeValue> graph = ValueGraphBuilder.<Long, EdgeValue>directed()
                .allowsSelfLoops(true)
                .build();
        graph.putEdgeValue(1L, 1L, parallelJobs);

        LineageGraphSnapshot snapshot = LineageGraphSnapshot.copyOf(graph, Map.of(1L, ORDERS), 7,
                Instant.parse("2026-07-29T00:00:00Z"));
        graph.removeEdge(1L, 1L);

        assertThat(snapshot.graph().hasEdgeConnecting(1L, 1L)).isTrue();
        assertThat(snapshot.graph().edgeValueOrDefault(1L, 1L, null).jobRefs()).hasSize(2);
        assertThat(snapshot.meta().hasCycle()).isTrue();
        assertThat(snapshot.meta().logicalEdgeCount()).isEqualTo(1);
        assertThat(snapshot.meta().physicalEdgeCount()).isEqualTo(2);
    }

    @Test
    void rejectsGraphsThatDoNotAllowRealWorldSelfLoops() {
        MutableValueGraph<Long, EdgeValue> graph = ValueGraphBuilder.<Long, EdgeValue>directed().build();
        graph.addNode(1L);

        assertThatThrownBy(() -> LineageGraphSnapshot.copyOf(graph, Map.of(1L, ORDERS), 1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allow self loops");
    }
}
