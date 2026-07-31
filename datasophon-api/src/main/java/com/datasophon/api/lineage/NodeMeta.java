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

/** 血缘节点侧表元数据；图内仅保存数据库节点 ID。 */
public record NodeMeta(long id, long clusterId, String connector, String catalogName, String databaseName,
        String tableName, String canonicalName, String dwLayer) {

    public NodeMeta {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (clusterId <= 0) {
            throw new IllegalArgumentException("clusterId must be positive");
        }
        connector = Objects.requireNonNull(connector, "connector");
        catalogName = Objects.requireNonNull(catalogName, "catalogName");
        databaseName = Objects.requireNonNull(databaseName, "databaseName");
        tableName = Objects.requireNonNull(tableName, "tableName");
        canonicalName = Objects.requireNonNull(canonicalName, "canonicalName");
    }
}
