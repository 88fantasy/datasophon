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

import com.datasophon.api.controller.observability.OtelCollectorController;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.Result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtelCollectorControllerTest {

    @Test
    void push_delegates_to_service_and_returns_success() {
        OtelCollectorConfigService svc = mock(OtelCollectorConfigService.class);
        ExecResult ok = new ExecResult();
        ok.setExecResult(true);
        when(svc.pushNodeConfig(eq(1), eq("app1"), any())).thenReturn(ok);

        OtelCollectorController c = new OtelCollectorController(svc);
        Result result = c.push(1, "app1");

        assertEquals(200, result.getCode());
        verify(svc).pushNodeConfig(eq(1), eq("app1"), any());
    }

    @Test
    void push_delegates_to_service_and_returns_error_on_failure() {
        OtelCollectorConfigService svc = mock(OtelCollectorConfigService.class);
        ExecResult fail = new ExecResult();
        fail.setExecResult(false);
        when(svc.pushNodeConfig(eq(2), eq("node2"), any())).thenReturn(fail);

        OtelCollectorController c = new OtelCollectorController(svc);
        Result result = c.push(2, "node2");

        assertEquals(500, result.getCode());
        verify(svc).pushNodeConfig(eq(2), eq("node2"), any());
    }
}
