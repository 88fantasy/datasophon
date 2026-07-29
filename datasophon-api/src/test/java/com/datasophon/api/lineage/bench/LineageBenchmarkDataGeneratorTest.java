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

package com.datasophon.api.lineage.bench;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.api.lineage.bench.LineageBenchmarkDataGenerator.EdgeSeed;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class LineageBenchmarkDataGeneratorTest {

    @Test
    void generatesExactPhysicalShapeWithSelfLoopsParallelEdgesAndSuperNode() {
        List<Long> jobIds = LongStream.rangeClosed(1, LineageBenchmarkDataGenerator.JOB_COUNT).boxed().toList();
        List<Long> nodeIds = LongStream.rangeClosed(1, LineageBenchmarkDataGenerator.NODE_COUNT).boxed().toList();

        List<EdgeSeed> edges = LineageBenchmarkDataGenerator.generateEdges(jobIds, nodeIds);
        Map<Endpoint, Long> endpointCounts = edges.stream().collect(Collectors.groupingBy(
                edge -> new Endpoint(edge.srcNodeId(), edge.dstNodeId()), Collectors.counting()));
        long superNodeInDegree = edges.stream().filter(edge -> edge.dstNodeId() == 1).count();

        assertThat(edges).hasSize(LineageBenchmarkDataGenerator.EDGE_COUNT);
        assertThat(edges).anyMatch(edge -> edge.srcNodeId() == edge.dstNodeId());
        assertThat(endpointCounts.values()).anyMatch(count -> count > 1);
        assertThat(superNodeInDegree).isGreaterThan(900);
    }

    private record Endpoint(long srcNodeId, long dstNodeId) {
    }
}
