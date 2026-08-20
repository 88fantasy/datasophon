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

import com.datasophon.api.enums.Status;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterRoleUserService;
import com.datasophon.api.utils.SecurityUtils;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.UserInfoEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ManageMode;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/** 接管 API 的集群模式和用户权限门禁。 */
@Component
public class K8sTakeoverAccessGuard {

    private final ClusterInfoService clusterInfoService;
    private final ClusterRoleUserService clusterRoleUserService;
    private final HttpServletRequest request;

    public K8sTakeoverAccessGuard(ClusterInfoService clusterInfoService,
                                  ClusterRoleUserService clusterRoleUserService,
                                  HttpServletRequest request) {
        this.clusterInfoService = clusterInfoService;
        this.clusterRoleUserService = clusterRoleUserService;
        this.request = request;
    }

    /** Controller 入口使用：同时校验当前用户和接管集群身份。 */
    public void requireAccess(Integer clusterId) {
        UserInfoEntity user = (UserInfoEntity) request.getAttribute(Constants.SESSION_USER);
        if (user == null || (!SecurityUtils.isAdmin(user)
                && !clusterRoleUserService.isClusterManager(user.getId(), String.valueOf(clusterId)))) {
            throw new ServiceException(Status.USER_NO_OPERATION_PERM);
        }
        requireImportedCluster(clusterId);
    }

    /** 关键写 Service 使用：即使绕过 Controller，也只允许写平台内的接管登记数据。 */
    public void requireImportedCluster(Integer clusterId) {
        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        if (cluster == null) {
            throw new BusinessHintException("集群不存在");
        }
        if (!ClusterArchType.k8s.equals(cluster.getArchType())) {
            throw new BusinessHintException("只有 K8s 集群支持接管操作");
        }
        if (!ManageMode.IMPORTED.equals(cluster.getManageMode())) {
            throw new BusinessHintException("当前集群不是接管模式，不能执行接管操作");
        }
    }
}
