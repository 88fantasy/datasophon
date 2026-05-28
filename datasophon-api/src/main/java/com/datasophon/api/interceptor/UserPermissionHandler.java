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

import com.datasophon.api.enums.Status;
import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.ClusterRoleUserService;
import com.datasophon.api.utils.SecurityUtils;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.UserInfoEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@Component
public class UserPermissionHandler implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(UserPermissionHandler.class);
    
    @Autowired
    private ClusterRoleUserService clusterUserService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        boolean requireCheckAdminIdentity = false;
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            UserPermission annotation = handlerMethod.getMethod().getAnnotation(UserPermission.class);
            requireCheckAdminIdentity = annotation != null;
        }

        if (requireCheckAdminIdentity) {
            boolean hasRight = false;
            UserInfoEntity authUser = (UserInfoEntity) request.getSession().getAttribute(Constants.SESSION_USER);
            if (authUser != null && SecurityUtils.isAdmin(authUser)) {
                Map<String, String[]> parameterMap = request.getParameterMap();
                if (parameterMap.containsKey("clusterId")) {
                    String[] clusterIds = parameterMap.get("clusterId");
                    hasRight = clusterUserService.isClusterManager(authUser.getId(), clusterIds[0]);
                }
            }
            if (!hasRight) {
                return true;
            }
            throw new ServiceException(Status.USER_NO_OPERATION_PERM);
        }

        return true;
    }
}
