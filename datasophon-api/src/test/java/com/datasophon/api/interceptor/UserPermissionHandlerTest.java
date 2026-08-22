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

package com.datasophon.api.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.ClusterRoleUserService;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.UserInfoEntity;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class UserPermissionHandlerTest {

    private ClusterRoleUserService clusterRoleUserService;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private UserPermissionHandler handler;
    private HandlerMethod securedMethod;

    @BeforeEach
    void setUp() throws Exception {
        clusterRoleUserService = mock(ClusterRoleUserService.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        handler = new UserPermissionHandler(clusterRoleUserService);
        Method method = SecuredController.class.getDeclaredMethod("secured");
        securedMethod = new HandlerMethod(new SecuredController(), method);
        when(request.getParameterMap()).thenReturn(Map.of());
    }

    @Test
    void adminIsAllowed() {
        givenUser(1);

        assertThat(handler.preHandle(request, response, securedMethod)).isTrue();
    }

    @Test
    void clusterManagerWithQueryClusterIdIsAllowed() {
        givenUser(2);
        when(request.getParameterMap()).thenReturn(Map.of("clusterId", new String[]{"7"}));
        when(clusterRoleUserService.isClusterManager(2, "7")).thenReturn(true);

        assertThat(handler.preHandle(request, response, securedMethod)).isTrue();
    }

    @Test
    void unrelatedUserIsRejected() {
        givenUser(2);

        assertThatThrownBy(() -> handler.preHandle(request, response, securedMethod))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void missingAuthenticatedUserIsRejected() {
        assertThatThrownBy(() -> handler.preHandle(request, response, securedMethod))
                .isInstanceOf(ServiceException.class);
    }

    private void givenUser(int id) {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(id);
        when(request.getAttribute(Constants.SESSION_USER)).thenReturn(user);
    }

    private static class SecuredController {
        @UserPermission
        void secured() {
        }
    }
}
