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

package com.datasophon.api.configuration;

import com.datasophon.api.lineage.CanonicalNameResolver;
import com.datasophon.api.lineage.IngestMetrics;
import com.datasophon.api.lineage.LineageEventDecoder;
import com.datasophon.api.lineage.LineageGenerationReader;
import com.datasophon.api.lineage.LineageGraphQuery;
import com.datasophon.api.lineage.LineageGraphSnapshotHolder;
import com.datasophon.api.lineage.LineageIngestService;
import com.datasophon.api.lineage.LineageLeaseGuard;
import com.datasophon.api.lineage.LineageMasterLease;
import com.datasophon.api.lineage.LineageRebuildCoordinator;
import com.datasophon.api.lineage.LineageStructureChangedListener;
import com.datasophon.api.lineage.MysqlSnapshotLoader;
import com.datasophon.api.lineage.StructuralHashCalculator;
import com.datasophon.api.lineage.WatermarkExtractor;
import com.datasophon.api.lineage.event.OpenLineageEventDecoder;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Spring assembly for the DB-authoritative lineage write and projection paths. */
@Configuration
public class LineageConfiguration {

    @Bean
    public LineageGraphSnapshotHolder lineageGraphSnapshotHolder() {
        return new LineageGraphSnapshotHolder();
    }

    @Bean
    public LineageGraphQuery lineageGraphQuery() {
        return new LineageGraphQuery();
    }

    @Bean
    public LineageGenerationReader lineageGenerationReader(JdbcTemplate jdbcTemplate) {
        return new LineageGenerationReader(jdbcTemplate);
    }

    @Bean
    public MysqlSnapshotLoader mysqlSnapshotLoader(JdbcTemplate jdbcTemplate) {
        return new MysqlSnapshotLoader(jdbcTemplate);
    }

    @Bean("lineageReadTransaction")
    public TransactionTemplate lineageReadTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setReadOnly(true);
        return transaction;
    }

    @Bean("lineageWriteTransaction")
    public TransactionTemplate lineageWriteTransaction(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public LineageRebuildCoordinator lineageRebuildCoordinator(
                                                               LineageGraphSnapshotHolder snapshotHolder,
                                                               MysqlSnapshotLoader snapshotLoader,
                                                               @Qualifier("lineageReadTransaction") TransactionTemplate readTransaction) {
        return new LineageRebuildCoordinator(snapshotHolder, snapshotLoader, readTransaction);
    }

    @Bean
    public LineageEventDecoder lineageEventDecoder() {
        return new OpenLineageEventDecoder();
    }

    @Bean
    public CanonicalNameResolver canonicalNameResolver() {
        return new CanonicalNameResolver.Default();
    }

    @Bean
    public StructuralHashCalculator structuralHashCalculator(ObjectMapper objectMapper) {
        return new StructuralHashCalculator(objectMapper);
    }

    @Bean
    public WatermarkExtractor watermarkExtractor() {
        return new WatermarkExtractor.Default();
    }

    @Bean
    public IngestMetrics lineageIngestMetrics() {
        return IngestMetrics.NOOP;
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    public LineageMasterLease lineageMasterLease(
                                                 @Value("${spring.datasource.url}") String jdbcUrl,
                                                 @Value("${spring.datasource.username}") String username,
                                                 @Value("${spring.datasource.password}") String password,
                                                 @Value("${datasophon.lineage.lease.enabled:true}") boolean enabled) {
        return new LineageMasterLease(jdbcUrl, username, password, enabled);
    }

    @Bean
    public LineageLeaseGuard lineageLeaseGuard(LineageMasterLease lease) {
        return new LineageLeaseGuard(lease);
    }

    @Bean
    public LineageIngestService lineageIngestService(
                                                     JdbcTemplate jdbcTemplate,
                                                     @Qualifier("lineageWriteTransaction") TransactionTemplate writeTransaction,
                                                     LineageEventDecoder eventDecoder,
                                                     CanonicalNameResolver canonicalNameResolver,
                                                     StructuralHashCalculator hashCalculator,
                                                     WatermarkExtractor watermarkExtractor,
                                                     ApplicationEventPublisher eventPublisher,
                                                     IngestMetrics ingestMetrics) {
        return new LineageIngestService(jdbcTemplate, writeTransaction, eventDecoder, canonicalNameResolver,
                hashCalculator, watermarkExtractor, eventPublisher, ingestMetrics);
    }

    @Bean
    public LineageStructureChangedListener lineageStructureChangedListener(
                                                                           LineageRebuildCoordinator coordinator) {
        return new LineageStructureChangedListener(coordinator);
    }
}
