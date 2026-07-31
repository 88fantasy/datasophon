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
     * Handles two namespace shapes:
     *
     * <ul>
     *   <li><b>Catalog-style</b> — {@code connector://catalog/database} with a bare table name.
     *       Unconfirmed against any real provider (TODO L0-#2, Hive/Paimon/Iceberg pending).</li>
     *   <li><b>JDBC-style</b> — {@code connector://host:port} with a {@code database.table} name.
     *       Confirmed 2026-07-30 against real {@code openlineage-spark} 1.29.0 output (MySQL and
     *       Doris-over-JDBC both produce this shape); see
     *       {@code docs/monitoring/data-lineage-verification.md} §3.5. Doris resolves under the
     *       {@code mysql} connector here because the generic JDBC facet cannot see past the wire
     *       protocol — rewriting it to a {@code doris} connector needs a host:port allowlist that
     *       depends on production topology, not something this resolver can infer.</li>
     * </ul>
     */
    final class Default implements CanonicalNameResolver {

        private final DwLayerInferrer dwLayerInferrer;

        public Default() {
            this(new DwLayerInferrer());
        }

        public Default(DwLayerInferrer dwLayerInferrer) {
            this.dwLayerInferrer = java.util.Objects.requireNonNull(dwLayerInferrer, "dwLayerInferrer");
        }

        @Override
        public Optional<ResolvedDataset> resolve(DatasetIdentity dataset) {
            String namespace = dataset.namespace().trim();
            String name = dataset.name().trim();
            int schemeSeparator = namespace.indexOf("://");
            if (schemeSeparator <= 0 || name.isEmpty()) {
                return Optional.empty();
            }
            String connector = namespace.substring(0, schemeSeparator);
            String path = namespace.substring(schemeSeparator + 3);
            if (path.isBlank()) {
                return Optional.empty();
            }
            return path.contains("/") ? resolveCatalogStyle(connector, path, name)
                    : resolveJdbcStyle(connector, path, name);
        }

        private Optional<ResolvedDataset> resolveCatalogStyle(String connector, String path, String table) {
            if (table.isEmpty() || table.contains("/")) {
                return Optional.empty();
            }
            String[] segments = path.split("/", -1);
            if (segments.length != 2 || segments[0].isBlank() || segments[1].isBlank()) {
                return Optional.empty();
            }
            String canonicalName = connector + "://" + segments[0] + "/" + segments[1] + "/" + table;
            return Optional.of(new ResolvedDataset(connector, segments[0], segments[1], table, canonicalName,
                    dwLayerInferrer.infer(segments[1], table)));
        }

        private Optional<ResolvedDataset> resolveJdbcStyle(String connector, String hostPort, String name) {
            int firstDot = name.indexOf('.');
            if (firstDot <= 0 || firstDot == name.length() - 1 || name.contains("/")
                    || name.indexOf('.', firstDot + 1) >= 0) {
                return Optional.empty();
            }
            String database = name.substring(0, firstDot);
            String table = name.substring(firstDot + 1);
            if (database.isBlank() || table.isBlank()) {
                return Optional.empty();
            }
            String canonicalName = connector + "://" + hostPort + "/" + database + "/" + table;
            return Optional.of(new ResolvedDataset(connector, hostPort, database, table, canonicalName,
                    dwLayerInferrer.infer(database, table)));
        }
    }
}
