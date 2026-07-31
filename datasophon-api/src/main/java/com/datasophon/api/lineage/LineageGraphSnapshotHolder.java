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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按集群分片持有不可变血缘快照，每个分片整体原子发布。
 *
 * <p>节点按集群彻底隔离（L3/D4），因此每个 clusterId 拥有独立的快照与独立的代际序列；
 * 一个集群的发布不影响另一个集群的可读性。</p>
 */
public final class LineageGraphSnapshotHolder {

    private final ConcurrentMap<Long, LineageGraphSnapshot> published = new ConcurrentHashMap<>();

    /**
     * 返回一次原子读取到的指定集群快照引用。
     *
     * <p><strong>只允许 GET 链路调用</strong>；写侧不得读取内存图参与任何权威写入判断。
     * 调用方必须从返回的同一个快照对象读取 generation 与图数据，不能再读取独立代际字段。</p>
     */
    public Optional<LineageGraphSnapshot> getForQuery(long clusterId) {
        return Optional.ofNullable(published.get(clusterId));
    }

    long currentGeneration(long clusterId) {
        LineageGraphSnapshot snapshot = published.get(clusterId);
        return snapshot == null ? -1 : snapshot.generation();
    }

    /**
     * 发布指定集群的快照；仅当其代际不旧于已发布代际时生效。
     *
     * <p>用 {@code compute} 做单键原子更新，避免旧版本 {@code synchronized} 造成的跨集群串行。</p>
     */
    PublishResult publishIfNotOlder(long clusterId, LineageGraphSnapshot next) {
        Objects.requireNonNull(next, "next");
        LineageGraphSnapshot winner = published.compute(clusterId, (key, current) -> {
            long currentGeneration = current == null ? -1 : current.generation();
            return next.generation() < currentGeneration ? current : next;
        });
        return winner == next
                ? new PublishResult(true, next.generation())
                : new PublishResult(false, winner.generation());
    }

    record PublishResult(boolean published, long currentGeneration) {
    }
}
