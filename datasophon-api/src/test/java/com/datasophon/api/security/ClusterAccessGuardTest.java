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

package com.datasophon.api.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.service.ClusterRoleUserService;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.UserInfoEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

class ClusterAccessGuardTest {

    private ClusterRoleUserService clusterRoleUserService;
    private HttpServletRequest request;
    private ClusterAccessGuard guard;

    @BeforeEach
    void setUp() {
        clusterRoleUserService = mock(ClusterRoleUserService.class);
        request = mock(HttpServletRequest.class);
        guard = new ClusterAccessGuard(clusterRoleUserService, request);
    }

    @Test
    void adminIsAllowed() {
        givenUser(1);

        assertThatCode(() -> guard.requireAccess(7)).doesNotThrowAnyException();
    }

    @Test
    void clusterManagerIsAllowed() {
        givenUser(2);
        when(clusterRoleUserService.isClusterManager(2, "7")).thenReturn(true);

        assertThatCode(() -> guard.requireAccess(7)).doesNotThrowAnyException();
    }

    @Test
    void unrelatedUserIsRejected() {
        givenUser(2);

        assertThatThrownBy(() -> guard.requireAccess(7)).isInstanceOf(ServiceException.class);
    }

    @Test
    void missingAuthenticatedUserIsRejected() {
        assertThatThrownBy(() -> guard.requireAccess(7)).isInstanceOf(ServiceException.class);
    }

    @Test
    void missingClusterIdIsRejected() {
        givenUser(1);

        assertThatThrownBy(() -> guard.requireAccess(null)).isInstanceOf(ServiceException.class);
    }

    private void givenUser(int id) {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(id);
        when(request.getAttribute(Constants.SESSION_USER)).thenReturn(user);
    }
}
