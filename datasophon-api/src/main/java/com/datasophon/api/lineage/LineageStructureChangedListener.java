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

import java.util.Objects;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Reduces projection latency after a committed structural write; correctness remains DB-driven. */
public final class LineageStructureChangedListener {

    private final LineageRebuildCoordinator coordinator;

    public LineageStructureChangedListener(LineageRebuildCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    /**
     * 结构变更落库后提前触发一次重建，仅用于降低延迟，不承担正确性。
     * 快照按集群分片（L3/D5），因此只重建事件所属集群的分片，不波及其他集群。
     * 这里只触发重建，绝不修改或读取内存快照。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLineageChanged(LineageStructureChangedEvent event) {
        coordinator.requestRebuild(event.clusterId(), LineageRebuildCoordinator.Trigger.EVENT);
    }
}
