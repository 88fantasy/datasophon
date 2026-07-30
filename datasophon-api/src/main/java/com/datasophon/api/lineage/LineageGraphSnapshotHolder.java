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

/** 使用单个 volatile 引用整体发布不可变血缘快照。 */
public final class LineageGraphSnapshotHolder {

    private volatile LineageGraphSnapshot published;

    /**
     * 返回一次原子读取到的快照引用。
     *
     * <p><strong>只允许 GET 链路调用</strong>；写侧不得读取内存图参与任何权威写入判断。
     * 调用方必须从返回的同一个快照对象读取 generation 与图数据，不能再读取独立代际字段。</p>
     */
    public Optional<LineageGraphSnapshot> getForQuery() {
        LineageGraphSnapshot snapshot = published;
        return Optional.ofNullable(snapshot);
    }

    synchronized PublishResult publishIfNotOlder(LineageGraphSnapshot next) {
        Objects.requireNonNull(next, "next");
        LineageGraphSnapshot current = published;
        long currentGeneration = current == null ? -1 : current.generation();
        if (next.generation() < currentGeneration) {
            return new PublishResult(false, currentGeneration);
        }
        published = next;
        return new PublishResult(true, next.generation());
    }

    record PublishResult(boolean published, long currentGeneration) {
    }
}
