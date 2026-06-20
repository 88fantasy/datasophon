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

package com.datasophon.api.observability;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class OtelSchemaOrchestratorTest {

    private final OtelExporterSwitchService switchService = mock(OtelExporterSwitchService.class);
    private final DorisJdbcClientFactory jdbcFactory = mock(DorisJdbcClientFactory.class);
    private final OtelCredentialService credentialService = mock(OtelCredentialService.class);
    private final OtelSchemaRunner schemaRunner = mock(OtelSchemaRunner.class);
    private final OtelSchemaOrchestrator orchestrator =
            new OtelSchemaOrchestrator(switchService, jdbcFactory, credentialService, schemaRunner);

    @Test
    void skipsSchemaUntilDorisIsReady() {
        when(switchService.isDorisReady(7)).thenReturn(false);

        orchestrator.applyIfReady(7);

        verify(jdbcFactory, never()).create(7);
        verify(schemaRunner, never()).apply(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void appliesSchemaWithClusterCredentialsWhenDorisIsReady() {
        JdbcClient jdbc = mock(JdbcClient.class);
        OtelCredentials credentials = new OtelCredentials("collector-secret", "reader-secret");
        when(switchService.isDorisReady(7)).thenReturn(true);
        when(jdbcFactory.create(7)).thenReturn(jdbc);
        when(credentialService.getOrCreate(7)).thenReturn(credentials);

        orchestrator.applyIfReady(7);

        verify(schemaRunner).apply(jdbc, credentials);
    }
}
