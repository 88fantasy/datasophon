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

import com.datasophon.api.doris.DorisReadinessService;
import com.datasophon.api.hook.ServiceHook;
import com.datasophon.api.hook.ServiceHookContext;

import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OtelSchemaInitHook implements ServiceHook {

    private final OtelSchemaOrchestrator orchestrator;

    private final DorisReadinessService readinessService;

    @Override
    public String getType() {
        return "otelSchemaInit";
    }

    @Override
    public boolean isReady(ServiceHookContext context) {
        return context.getClusterId() != null && readinessService.waitUntilClusterReady(context.getClusterId(),
                maxAttempts(context), intervalMs(context));
    }

    @Override
    public void invoke(ServiceHookContext context) {
        if (context.getClusterId() != null) {
            orchestrator.applyIfReady(context.getClusterId());
        }
    }

    private int maxAttempts(ServiceHookContext context) {
        return numberParam(context.getParams(), "maxAttempts", 24).intValue();
    }

    private long intervalMs(ServiceHookContext context) {
        return numberParam(context.getParams(), "intervalMs", 5000L).longValue();
    }

    private Number numberParam(Map<String, Object> params, String name, Number defaultValue) {
        Object value = params == null ? null : params.get(name);
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                // 使用默认值，避免无效 DDL 参数中断后续 hook。
            }
        }
        return defaultValue;
    }
}
