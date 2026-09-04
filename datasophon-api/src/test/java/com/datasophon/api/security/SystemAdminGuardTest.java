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

package com.datasophon.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import com.datasophon.api.enums.Status;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.UserInfoEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

class SystemAdminGuardTest {

    private HttpServletRequest request;
    private SystemAdminGuard guard;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        guard = new SystemAdminGuard(request);
    }

    @Test
    void systemAdminIsAllowed() {
        givenUser(1);

        assertThatCode(() -> guard.requireAdmin()).doesNotThrowAnyException();
    }

    @Test
    void clusterManagerIsRejectedWithForbidden() {
        assertForbiddenForUser(2);
    }

    @Test
    void ordinaryUserIsRejectedWithForbidden() {
        assertForbiddenForUser(3);
    }

    @Test
    void missingUserIsRejectedWithForbidden() {
        assertThatThrownBy(() -> guard.requireAdmin())
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(FORBIDDEN);
                    assertThat(exception.getReason()).isEqualTo(Status.USER_NO_OPERATION_PERM.getMsg());
                });
    }

    private void assertForbiddenForUser(int userId) {
        givenUser(userId);

        assertThatThrownBy(() -> guard.requireAdmin())
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(FORBIDDEN);
                    assertThat(exception.getReason()).isEqualTo(Status.USER_NO_OPERATION_PERM.getMsg());
                });
    }

    private void givenUser(int id) {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(id);
        when(request.getAttribute(Constants.SESSION_USER)).thenReturn(user);
    }
}
