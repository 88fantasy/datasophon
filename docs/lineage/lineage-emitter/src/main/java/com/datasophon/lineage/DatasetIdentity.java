package com.datasophon.lineage;

/**
 * OpenLineage dataset identity as sent to Gravitino's {@code /api/lineage}. Equality is exact
 * string match on (namespace, name) — that is what Gravitino's {@code lineage_dataset} table
 * dedupes on (see {@code JdbcLineageStorage.hashIdentity}), so job1's output identity must be
 * byte-identical to job2's input identity for the two jobs to share one graph node (A4).
 */
public record DatasetIdentity(String namespace, String name) {}
