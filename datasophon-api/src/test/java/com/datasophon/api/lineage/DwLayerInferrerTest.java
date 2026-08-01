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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DwLayerInferrerTest {

    private final DwLayerInferrer inferrer = new DwLayerInferrer();

    @Test
    void defaultRulesMatchEachStandardPrefixCaseInsensitively() {
        assertThat(inferrer.infer(null, "ods_orders")).isEqualTo("ODS");
        assertThat(inferrer.infer(null, "DWD_ORDERS")).isEqualTo("DWD");
        assertThat(inferrer.infer(null, "Dws_Orders")).isEqualTo("DWS");
        assertThat(inferrer.infer(null, "dim_customer")).isEqualTo("DIM");
        assertThat(inferrer.infer(null, "ads_report")).isEqualTo("ADS");
        assertThat(inferrer.infer(null, "tmp_scratch")).isEqualTo("TMP");
        assertThat(inferrer.infer(null, "temp_scratch")).isEqualTo("TMP");
    }

    @Test
    void tableNameIsCheckedBeforeDatabaseName() {
        // 库名前缀是 dwd_，表名前缀是 ods_ —— 表名优先命中 ODS，不看库名。
        assertThat(inferrer.infer("dwd_prod", "ods_orders")).isEqualTo("ODS");
    }

    @Test
    void databaseNameIsUsedOnlyWhenTableNameDoesNotMatch() {
        assertThat(inferrer.infer("dwd_prod", "orders")).isEqualTo("DWD");
    }

    @Test
    void returnsNullWhenNeitherTableNorDatabaseMatchesAnyRule() {
        assertThat(inferrer.infer("prod", "orders")).isNull();
        assertThat(inferrer.infer(null, null)).isNull();
        assertThat(inferrer.infer("", "")).isNull();
    }

    @Test
    void customRulesOverrideDefaultsAndPreserveInsertionOrderForOverlappingPrefixes() {
        Map<String, String> customRules = new LinkedHashMap<>();
        // "dwd" 是 "dwd_" 的前缀，两条规则对 "dwd_orders" 都能匹配——顺序决定谁赢。
        customRules.put("dwd", "DWD_GENERIC");
        customRules.put("dwd_", "DWD_SPECIFIC");
        DwLayerInferrer custom = new DwLayerInferrer(customRules);

        // "dwd" 先注册，先匹配者胜：命中 "dwd" 而不是更具体的 "dwd_"。
        assertThat(custom.infer(null, "dwd_orders")).isEqualTo("DWD_GENERIC");
        // 默认规则不再生效。
        assertThat(custom.infer(null, "ods_orders")).isNull();
    }

    @Test
    void blankOrNullRuleEntriesAreIgnored() {
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("ods_", "ODS");
        rules.put(" ", "SHOULD_BE_IGNORED");
        rules.put("junk_", null);
        DwLayerInferrer custom = new DwLayerInferrer(rules);

        assertThat(custom.infer(null, "ods_orders")).isEqualTo("ODS");
        assertThat(custom.infer(null, "junk_orders")).isNull();
    }
}
