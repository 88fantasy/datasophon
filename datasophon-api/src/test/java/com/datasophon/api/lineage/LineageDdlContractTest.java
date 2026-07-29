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

import com.datasophon.api.migration.DatabaseMigration;
import com.datasophon.api.migration.Migration;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LineageDdlContractTest {

    @Test
    void databaseMigrationDiscoversVersion225WithBothScripts() {
        TreeSet<Migration> migrations =
                ReflectionTestUtils.invokeMethod(new DatabaseMigration(null), "getAllMigrations");

        Migration latest = migrations.last();
        assertThat(latest.getVersion()).isEqualTo("2.2.5");
        assertThat(latest.getUpgradeDDLFile().getFilename()).isEqualTo("V2.2.5__DDL.sql");
        assertThat(latest.getUpgradeDMLFile().getFilename()).isEqualTo("V2.2.5__DML.sql");
    }

    @Test
    void migrationContainsSevenIdempotentTablesAndRequiredKeys() throws IOException {
        String resource = "/db/migration/2.2.5/V2.2.5__DDL.sql";
        assertThat(getClass().getResource("/db/migration/2.2.5/V2.2.5__DML.sql"))
                .as("DatabaseMigration requires a DML resource for every version")
                .isNotNull();
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(StringUtils.countMatches(sql, "CREATE TABLE IF NOT EXISTS")).isEqualTo(7);
            assertThat(sql)
                    .contains("`current_structural_hash` CHAR(64)")
                    .contains("`current_watermark` BIGINT")
                    .contains("UNIQUE KEY `uk_job_version` (`job_id`, `version`)")
                    .doesNotContain("UNIQUE KEY `uk_job_version` (`job_id`, `content_hash`)")
                    .contains("UNIQUE KEY `uk_event` (`producer`, `run_id`, `event_type`)")
                    // 作业身份必须 UNIQUE：普通 KEY 会让写路径的 FOR UPDATE 锁不住不存在的行，
                    // 并发首次事件产生两个 job_id（三轮自审 F1）
                    .contains("UNIQUE KEY `uk_data_job_identity` (`cluster_id`, `engine`, `job_name`)")
                    .contains("KEY `idx_edge_current` (`is_current`, `src_node_id`, `dst_node_id`)")
                    .contains("INSERT IGNORE INTO `t_ddh_lineage_generation`");
        }
    }
}
