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

package com.datasophon.api.doris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 断言绑定到两个环境的 <b>实测</b> schema（2026-09-03）：
 * 远端 K8s 存算分离 doris-3.0.8-rc01 Cloud Mode，与 ddh-01 doris-4.1.3-rc02。
 */
class DorisVersionProfileTest {

    private static final String V3_COMMENT = "Doris version doris-3.0.8-rc01-09b0cc49a6 (Cloud Mode)";
    private static final String V4_COMMENT = "Doris version doris-4.1.3-rc02-7126cf65d96";

    @Test
    void mapsMajorVersionFromVersionComment() {
        assertThat(DorisVersionProfile.of("Doris version doris-2.1.11-rc01")).isEqualTo(DorisVersionProfile.V2);
        assertThat(DorisVersionProfile.of(V3_COMMENT)).isEqualTo(DorisVersionProfile.V3);
        assertThat(DorisVersionProfile.of(V4_COMMENT)).isEqualTo(DorisVersionProfile.V4);
    }

    @Test
    void fallsBackToNewestKnownProfileForUnreadableVersion() {
        assertThat(DorisVersionProfile.of("Doris version doris-5.0.0")).isEqualTo(DorisVersionProfile.V4);
        assertThat(DorisVersionProfile.of("SomeVendorDB 1.2")).isEqualTo(DorisVersionProfile.V4);
        assertThat(DorisVersionProfile.of(null)).isEqualTo(DorisVersionProfile.V4);
        assertThat(DorisVersionProfile.of("")).isEqualTo(DorisVersionProfile.V4);
    }

    @Test
    void twoPointXIsReservedAndUnsupported() {
        assertThat(DorisVersionProfile.V2.supported()).isFalse();
        assertThat(DorisVersionProfile.V2.activeQueriesSql()).isNull();
        assertThat(DorisVersionProfile.V3.supported()).isTrue();
        assertThat(DorisVersionProfile.V4.supported()).isTrue();
    }

    @Test
    void threePointXOmitsColumnsThatDoNotExistThere() {
        // 3.0.8 实测：active_queries 无 USER，backend_active_tasks 无 WORKLOAD_GROUP_ID / 无 SPILL_*。
        assertThat(DorisVersionProfile.V3.activeQueriesSql()).doesNotContain("USER");
        assertThat(DorisVersionProfile.V3.backendActiveTasksSql())
                .doesNotContain("WORKLOAD_GROUP_ID")
                .doesNotContain("SPILL_");
        assertThat(DorisVersionProfile.V3.unsupportedFields())
                .containsExactlyInAnyOrder("spillBytes", "loadWorkloadGroup");
    }

    @Test
    void fourPointXKeepsTheFullColumnSet() {
        assertThat(DorisVersionProfile.V4.activeQueriesSql()).contains("`USER`");
        assertThat(DorisVersionProfile.V4.backendActiveTasksSql())
                .contains("WORKLOAD_GROUP_ID")
                .contains("SPILL_WRITE_BYTES_TO_LOCAL_STORAGE")
                .contains("SPILL_READ_BYTES_FROM_LOCAL_STORAGE");
        assertThat(DorisVersionProfile.V4.unsupportedFields()).isEmpty();
    }

    @Test
    void processlistColumnsAreAliasedToOneNamingAcrossVersions() {
        // 3.x 用 QUERY_ID/HOST/USER，4.x 用 QueryId/Host/User；别名让下游只认一套 key。
        assertThat(DorisVersionProfile.V3.processlistSql())
                .contains("QUERY_ID AS QueryId")
                .contains("HOST AS Host")
                .contains("`USER` AS `User`");
        assertThat(DorisVersionProfile.V4.processlistSql())
                .contains("QueryId")
                .contains("Host")
                .contains("`User`");
    }

    @Test
    void everySupportedProfileFiltersProcesslistAndBoundsRowCount() {
        for (DorisVersionProfile profile : DorisVersionProfile.values()) {
            if (!profile.supported()) {
                continue;
            }
            assertThat(profile.processlistSql()).contains("WHERE Command = 'Query'");
            assertThat(profile.activeQueriesSql()).endsWith("LIMIT " + DorisVersionProfile.SOURCE_LIMIT);
            assertThat(profile.backendActiveTasksSql()).endsWith("LIMIT " + DorisVersionProfile.SOURCE_LIMIT);
            assertThat(profile.processlistSql()).endsWith("LIMIT " + DorisVersionProfile.SOURCE_LIMIT);
        }
    }
}
