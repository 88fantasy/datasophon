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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.service.doris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;

import com.datasophon.api.controller.v2.V2ApiExceptionHandler;
import com.datasophon.api.doris.DorisAdminReaderFactory;
import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.dto.v2.DorisActiveTaskQueryDTO;
import com.datasophon.api.dto.v2.DorisActiveTaskResponseVO;
import com.datasophon.api.enums.Status;
import com.datasophon.api.security.SystemAdminGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

class DorisActiveTaskFacadeTest {

    private final SystemAdminGuard adminGuard = mock(SystemAdminGuard.class);
    private final DorisServiceInstanceValidator instanceValidator = mock(DorisServiceInstanceValidator.class);
    private final DorisAdminReaderFactory readerFactory = mock(DorisAdminReaderFactory.class);
    private final DorisActiveTaskQueryService queryService = mock(DorisActiveTaskQueryService.class);
    private final DorisActiveTaskFacade facade = new DorisActiveTaskFacade(
            adminGuard, instanceValidator, readerFactory, queryService);
    private final DorisAdminReaderFactory.DorisAdminConnection connection =
            new DorisAdminReaderFactory.DorisAdminConnection(
                    mock(JdbcClient.class), "ddh-01", 9030, "root", false, null);

    @BeforeEach
    void setUp() {
        when(readerFactory.create(7)).thenReturn(connection);
    }

    @Test
    void preservesForbiddenStatusFromTheAdminGuard() {
        ResponseStatusException forbidden = new ResponseStatusException(
                FORBIDDEN, Status.USER_NO_OPERATION_PERM.getMsg());
        when(adminGuard.requireAdmin()).thenThrow(forbidden);

        assertThatThrownBy(() -> facade.query(7, 8, null)).isSameAs(forbidden);
        verify(instanceValidator, never()).requireDorisInstance(7, 8);
        verify(readerFactory, never()).create(7);
    }

    @Test
    void preservesBadRequestStatusFromTheInstanceValidator() {
        ResponseStatusException badRequest = new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, Status.INSTANCE_MISMATCH.getMsg());
        when(instanceValidator.requireDorisInstance(7, 8)).thenThrow(badRequest);

        assertThatThrownBy(() -> facade.query(7, 8, null)).isSameAs(badRequest);
        verify(readerFactory, never()).create(7);
    }

    @Test
    void mapsMissingCapabilityToNotImplemented() {
        when(queryService.query(7, connection, new DorisActiveTaskQueryDTO()))
                .thenThrow(new DorisActiveTaskQueryService.CapabilityUnsupportedException());

        assertThatThrownBy(() -> facade.query(7, 8, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(NOT_IMPLEMENTED);
                    assertThat(exception.getReason()).isEqualTo(Status.DORIS_CAPABILITY_UNSUPPORTED.getMsg());
                });
    }

    @Test
    void mapsConnectionFailureToSafeBadGatewayResponse() {
        when(queryService.query(7, connection, new DorisActiveTaskQueryDTO()))
                .thenThrow(new RuntimeException("jdbc:mysql://user:password@fe:9030/secret"));

        ResponseStatusException exception = (ResponseStatusException) assertThatThrownBy(
                () -> facade.query(7, 8, null)).isInstanceOf(ResponseStatusException.class).actual();
        assertThat(exception.getStatusCode()).isEqualTo(BAD_GATEWAY);
        assertThat(exception.getReason()).isEqualTo(Status.DORIS_CONNECT_FAILED.getMsg());

        ResponseEntity<ApiResponse<Void>> response = new V2ApiExceptionHandler().handleException(exception);
        assertThat(response.getStatusCode()).isEqualTo(BAD_GATEWAY);
        assertThat(response.getBody().getErrorMessage()).isEqualTo(Status.DORIS_CONNECT_FAILED.getMsg());
        assertThat(response.getBody().getErrorMessage()).doesNotContain("jdbc:");
    }
}
