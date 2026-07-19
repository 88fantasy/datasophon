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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.doris.DorisReadinessService;
import com.datasophon.api.hook.ServiceHookContext;

import java.util.Map;

import org.junit.jupiter.api.Test;

class OtelSchemaInitHookTest {

    private final OtelSchemaOrchestrator orchestrator = mock(OtelSchemaOrchestrator.class);
    private final DorisReadinessService readinessService = mock(DorisReadinessService.class);
    private final OtelSchemaInitHook hook = new OtelSchemaInitHook(orchestrator, readinessService);

    @Test
    void delegatesReadinessWithDdlParameters() {
        ServiceHookContext context = context(7);
        context.setParams(Map.of("maxAttempts", 3, "intervalMs", 10));
        when(readinessService.waitUntilClusterReady(7, 3, 10)).thenReturn(true);

        assertTrue(hook.isReady(context));
        verify(readinessService).waitUntilClusterReady(7, 3, 10);
    }

    @Test
    void nullClusterIsNotReady() {
        assertFalse(hook.isReady(context(null)));
    }

    private static ServiceHookContext context(Integer clusterId) {
        ServiceHookContext context = new ServiceHookContext();
        context.setClusterId(clusterId);
        return context;
    }
}
