package com.datasophon.lineage;

import java.util.List;
import java.util.Map;

/**
 * Parsed form of one SQL file: an ordered sequence of statements to run through {@code
 * tEnv.executeSql(...)} (CREATE CATALOG / USE / CREATE TABLE / CREATE TEMPORARY TABLE / VIEW,
 * ...), a separate ordered list of INSERT statements to collect into a {@code StatementSet}, and
 * a registry of every {@code CREATE TEMPORARY TABLE} declared, keyed by its local (unqualified)
 * table name, holding the literal WITH-options — this is how {@link DatasetResolver} recovers the
 * physical source/sink location for connector tables that CompiledPlan JSON cannot resolve (see
 * class javadoc there).
 */
public record SqlScript(
    List<String> executeImmediately,
    List<String> insertStatements,
    Map<String, TempTableInfo> tempTables) {

  public record TempTableInfo(String name, Map<String, String> options) {}
}
