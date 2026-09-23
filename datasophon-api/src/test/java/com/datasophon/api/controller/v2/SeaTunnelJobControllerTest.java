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

package com.datasophon.api.controller.v2;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import com.datasophon.api.security.SystemAdminGuard;
import com.datasophon.api.service.seatunnel.SeaTunnelJobService;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SeaTunnelJobControllerTest {

    @Test
    void rejectsNonAdminBeforeRequestingZetaData() {
        SystemAdminGuard guard = mock(SystemAdminGuard.class);
        SeaTunnelJobService service = mock(SeaTunnelJobService.class);
        ResponseStatusException forbidden = new ResponseStatusException(FORBIDDEN, "无操作权限");
        doThrow(forbidden).when(guard).requireAdmin();
        SeaTunnelJobController controller = new SeaTunnelJobController(service, guard);

        assertThatThrownBy(() -> controller.overview(7, 8)).isSameAs(forbidden);
        verify(service, never()).overview(7, 8);
    }
}
