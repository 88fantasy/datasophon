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

import java.time.Instant;
import java.util.Objects;

/** 快照构建时的不可变元数据。 */
public record LineageSnapshotMeta(long generation, long targetGeneration, Instant builtAt, boolean stale,
        boolean degraded, boolean hasCycle, int nodeCount, long logicalEdgeCount, long physicalEdgeCount,
        String lastRebuildError) {

    public LineageSnapshotMeta {
        if (generation < 0 || targetGeneration < generation) {
            throw new IllegalArgumentException("generation must be non-negative and not exceed targetGeneration");
        }
        Objects.requireNonNull(builtAt, "builtAt");
        if (nodeCount < 0 || logicalEdgeCount < 0 || physicalEdgeCount < logicalEdgeCount) {
            throw new IllegalArgumentException("invalid graph counts");
        }
    }

    public static LineageSnapshotMeta fresh(long generation, Instant builtAt, boolean hasCycle, int nodeCount,
            long logicalEdgeCount, long physicalEdgeCount) {
        return new LineageSnapshotMeta(generation, generation, builtAt, false, false, hasCycle, nodeCount,
                logicalEdgeCount, physicalEdgeCount, null);
    }
}
