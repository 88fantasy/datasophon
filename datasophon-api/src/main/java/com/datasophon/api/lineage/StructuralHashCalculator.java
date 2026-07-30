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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Stable structural/content hash implementation for lineage definitions. */
public final class StructuralHashCalculator {

    private final NameNormalizer nameNormalizer;
    private final ObjectMapper objectMapper;

    public StructuralHashCalculator(ObjectMapper objectMapper) {
        this(NameNormalizer.IDENTITY, objectMapper);
    }

    public StructuralHashCalculator(NameNormalizer nameNormalizer, ObjectMapper objectMapper) {
        this.nameNormalizer = Objects.requireNonNull(nameNormalizer, "nameNormalizer");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String structuralHash(Collection<ResolvedDataset> inputs, Collection<ResolvedDataset> outputs) {
        List<String> normalizedInputs = normalizedNames(inputs);
        List<String> normalizedOutputs = normalizedNames(outputs);
        return sha256(String.join("\n", normalizedInputs) + "->" + String.join("\n", normalizedOutputs));
    }

    public String definitionText(String sqlQuery, Collection<ResolvedDataset> inputs,
                                 Collection<ResolvedDataset> outputs) {
        if (sqlQuery != null && !sqlQuery.isBlank()) {
            return sqlQuery;
        }
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("inputs", canonicalNames(inputs));
        definition.put("outputs", canonicalNames(outputs));
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize lineage definition", e);
        }
    }

    public String contentHash(String definitionText) {
        return sha256(Objects.requireNonNull(definitionText, "definitionText"));
    }

    private List<String> normalizedNames(Collection<ResolvedDataset> datasets) {
        // TODO L0-#7: 动态表名、临时表与日期分区归一规则尚未实机确认。
        return canonicalNames(datasets).stream()
                .map(nameNormalizer::normalize)
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> canonicalNames(Collection<ResolvedDataset> datasets) {
        return datasets.stream()
                .map(ResolvedDataset::canonicalName)
                .distinct()
                .sorted()
                .toList();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @FunctionalInterface
    public interface NameNormalizer {

        NameNormalizer IDENTITY = name -> name;

        String normalize(String canonicalName);
    }
}
