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

import java.util.Optional;

/** Converts a provider-neutral dataset identity into the database canonical name. */
@FunctionalInterface
public interface CanonicalNameResolver {

    Optional<ResolvedDataset> resolve(DatasetIdentity dataset);

    /**
     * The deliberately narrow default assumes namespace is {@code connector://catalog/database}
     * and name is the table segment.
     */
    final class Default implements CanonicalNameResolver {

        @Override
        public Optional<ResolvedDataset> resolve(DatasetIdentity dataset) {
            // TODO L0-#2: Gravitino 转换后的 namespace/name 拼写尚未实机确认。
            String namespace = dataset.namespace().trim();
            String table = dataset.name().trim();
            int schemeSeparator = namespace.indexOf("://");
            if (schemeSeparator <= 0 || table.isEmpty() || table.contains("/")) {
                return Optional.empty();
            }
            String connector = namespace.substring(0, schemeSeparator);
            String[] path = namespace.substring(schemeSeparator + 3).split("/", -1);
            if (path.length != 2 || path[0].isBlank() || path[1].isBlank()) {
                return Optional.empty();
            }
            String canonicalName = connector + "://" + path[0] + "/" + path[1] + "/" + table;
            return Optional.of(new ResolvedDataset(connector, path[0], path[1], table, canonicalName, null));
        }
    }
}
