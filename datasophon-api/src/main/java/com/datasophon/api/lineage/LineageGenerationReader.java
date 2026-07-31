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

import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 查询侧读取 MySQL 当前声明的血缘代际。
 *
 * <p>这次单行读取不属于快照重建事务，也不复用 {@link MysqlSnapshotLoader}。</p>
 */
public final class LineageGenerationReader {

    static final String GENERATION_SQL =
            "SELECT generation FROM t_ddh_lineage_generation WHERE cluster_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public LineageGenerationReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    /**
     * 读取指定集群的当前代际。
     *
     * <p>代际行按集群惰性创建（L3/D5 取消了单行种子），因此「行不存在」是尚未收到任何
     * 结构性事件的正常状态，返回 {@code 0} 而非报错。</p>
     */
    public long readCurrentGeneration(long clusterId) {
        List<Long> generations = jdbcTemplate.queryForList(GENERATION_SQL, Long.class, clusterId);
        return generations.isEmpty() ? 0L : generations.get(0);
    }
}
