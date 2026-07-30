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

import com.datasophon.api.lineage.event.OpenLineageEventDecoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Tag("mysql")
abstract class LineageMysqlTestSupport {

    static final String MYSQL_URL = System.getProperty(
            "lineage.test.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/datasophon_lineage_test"
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    static final String MYSQL_USERNAME = System.getProperty("lineage.test.mysql.username", "root");
    static final String MYSQL_PASSWORD = System.getProperty("lineage.test.mysql.password", "localmysql");

    static HikariDataSource dataSource;
    static JdbcTemplate jdbcTemplate;
    static DataSourceTransactionManager transactionManager;
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeAll
    static void initializeLineageDatabase() throws Exception {
        try (
                Connection connection = DriverManager.getConnection(serverUrl(), MYSQL_USERNAME, MYSQL_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE DATABASE IF NOT EXISTS datasophon_lineage_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL_URL);
        config.setUsername(MYSQL_USERNAME);
        config.setPassword(MYSQL_PASSWORD);
        config.setMaximumPoolSize(30);
        config.setPoolName("lineage-mysql-test");
        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);

        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("db/migration/2.2.5/V2.2.5__DDL.sql"));
        populator.execute(dataSource);
    }

    @BeforeEach
    void clearLineageTables() {
        jdbcTemplate.update("DELETE FROM t_ddh_lineage_edge");
        jdbcTemplate.update("DELETE FROM t_ddh_data_job_definition");
        jdbcTemplate.update("DELETE FROM t_ddh_lineage_parse_log");
        jdbcTemplate.update("DELETE FROM t_ddh_lineage_event");
        jdbcTemplate.update("DELETE FROM t_ddh_lineage_node");
        jdbcTemplate.update("DELETE FROM t_ddh_data_job");
        jdbcTemplate.update("UPDATE t_ddh_lineage_generation SET generation = 0 WHERE id = 1");
    }

    @AfterAll
    static void closeLineageDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    static LineageIngestService ingestService() {
        return ingestService(IngestMetrics.NOOP);
    }

    static LineageIngestService ingestService(IngestMetrics metrics) {
        return new LineageIngestService(
                jdbcTemplate,
                new TransactionTemplate(transactionManager),
                new OpenLineageEventDecoder(),
                new CanonicalNameResolver.Default(),
                new StructuralHashCalculator(OBJECT_MAPPER),
                new WatermarkExtractor.Default(),
                event -> {
                },
                metrics);
    }

    static ObjectNode event(String runId, String eventType, Instant watermark, String outputTable) {
        return event(runId, eventType, watermark, List.of("orders_raw"), List.of(outputTable),
                "insert into " + outputTable + " select * from orders_raw");
    }

    static ObjectNode event(String runId, String eventType, Instant watermark, List<String> inputs,
                            List<String> outputs, String sql) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("producer", "https://example.test/openlineage/spark");
        payload.put("engine", "spark");
        payload.put("eventType", eventType);
        payload.put("eventTime", watermark.plusSeconds(1).toString());
        payload.withObject("run").put("runId", runId)
                .withObject("facets")
                .withObject("nominalTime")
                .put("nominalStartTime", watermark.toString());
        ObjectNode job = payload.withObject("job");
        job.put("name", "daily-orders");
        job.withObject("facets")
                .withObject("jobType")
                .put("processingType", "BATCH")
                .put("integration", "SPARK");
        if (sql != null) {
            job.withObject("facets").withObject("sql").put("query", sql);
        }
        payload.set("inputs", datasets("ods", inputs));
        payload.set("outputs", datasets("dwd", outputs));
        return payload;
    }

    private static ArrayNode datasets(String database, List<String> tables) {
        ArrayNode result = OBJECT_MAPPER.createArrayNode();
        for (String table : tables) {
            result.addObject()
                    .put("namespace", "paimon://prod/" + database)
                    .put("name", table);
        }
        return result;
    }

    private static String serverUrl() {
        int queryIndex = MYSQL_URL.indexOf('?');
        String query = queryIndex >= 0 ? MYSQL_URL.substring(queryIndex) : "";
        String base = queryIndex >= 0 ? MYSQL_URL.substring(0, queryIndex) : MYSQL_URL;
        return base.substring(0, base.lastIndexOf('/') + 1) + query;
    }
}
