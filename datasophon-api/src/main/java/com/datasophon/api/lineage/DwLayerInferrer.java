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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 按命名约定推断数仓分层。
 *
 * <p>OpenLineage 事件本身不携带分层信息，而 {@code dw_layer} 是查询侧两个机制的输入：
 * {@code LineageGraphQuery} 的分层 BFS 优先级，以及分层概览。L1 的写路径始终传 {@code null}，
 * 导致 {@code layerDistance()} 恒为 {@code Integer.MAX_VALUE}、分层 BFS 静默退化成纯度数排序
 * （L3 §1.2 缺陷 1）。这里按业界通行的表名前缀约定补齐。</p>
 *
 * <p>先看表名，未命中再看库名；都不命中返回 {@code null} —— 宁可标记为未知，
 * 也不猜一个错误的层级去干扰 BFS 优先级。</p>
 */
public final class DwLayerInferrer {

    /** 前缀 → 层级；LinkedHashMap 保证匹配顺序稳定可预期。 */
    private static final Map<String, String> DEFAULT_RULES = defaultRules();

    private final Map<String, String> rules;

    public DwLayerInferrer() {
        this(DEFAULT_RULES);
    }

    public DwLayerInferrer(Map<String, String> rules) {
        Objects.requireNonNull(rules, "rules");
        Map<String, String> normalized = new LinkedHashMap<>();
        rules.forEach((prefix, layer) -> {
            if (prefix != null && !prefix.isBlank() && layer != null && !layer.isBlank()) {
                normalized.put(prefix.toLowerCase(Locale.ROOT), layer.toUpperCase(Locale.ROOT));
            }
        });
        // 必须保序：自定义规则里可能出现 dw_ 与 dwd_ 这类互为前缀的项，先匹配者胜。
        // Map.copyOf 不保证迭代顺序，不能用。
        this.rules = Collections.unmodifiableMap(normalized);
    }

    private static Map<String, String> defaultRules() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("ods_", "ODS");
        defaults.put("dwd_", "DWD");
        defaults.put("dws_", "DWS");
        defaults.put("dim_", "DIM");
        defaults.put("ads_", "ADS");
        defaults.put("tmp_", "TMP");
        defaults.put("temp_", "TMP");
        return defaults;
    }

    /**
     * @return 推断出的层级；无法判断时返回 {@code null}
     */
    public String infer(String databaseName, String tableName) {
        String fromTable = match(tableName);
        return fromTable != null ? fromTable : match(databaseName);
    }

    private String match(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> rule : rules.entrySet()) {
            if (lower.startsWith(rule.getKey())) {
                return rule.getValue();
            }
        }
        return null;
    }
}
