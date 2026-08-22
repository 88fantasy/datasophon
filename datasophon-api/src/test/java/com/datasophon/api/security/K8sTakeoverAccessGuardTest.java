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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterRoleUserService;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.UserInfoEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ManageMode;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

class K8sTakeoverAccessGuardTest {

    private static final int CLUSTER_ID = 7;

    @Test
    void allowsAdminToAccessImportedK8sCluster() {
        Fixture fixture = new Fixture(importedK8sCluster());
        fixture.user.setId(1);

        assertThatNoException().isThrownBy(() -> fixture.guard.requireAccess(CLUSTER_ID));
    }

    @Test
    void allowsAssignedClusterManager() {
        Fixture fixture = new Fixture(importedK8sCluster());
        fixture.user.setId(9);
        when(fixture.roleUserService.isClusterManager(9, "7")).thenReturn(true);

        assertThatNoException().isThrownBy(() -> fixture.guard.requireAccess(CLUSTER_ID));
    }

    @Test
    void rejectsUserWithoutClusterPermission() {
        Fixture fixture = new Fixture(importedK8sCluster());
        fixture.user.setId(9);

        assertThatThrownBy(() -> fixture.guard.requireAccess(CLUSTER_ID))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsManagedOrPhysicalCluster() {
        ClusterInfoEntity managed = importedK8sCluster();
        managed.setManageMode(ManageMode.MANAGED);
        Fixture managedFixture = new Fixture(managed);
        managedFixture.user.setId(1);
        assertThatThrownBy(() -> managedFixture.guard.requireAccess(CLUSTER_ID))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("不是接管模式");

        ClusterInfoEntity physical = importedK8sCluster();
        physical.setArchType(ClusterArchType.physical);
        Fixture physicalFixture = new Fixture(physical);
        physicalFixture.user.setId(1);
        assertThatThrownBy(() -> physicalFixture.guard.requireAccess(CLUSTER_ID))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("K8s 集群");
    }

    private static ClusterInfoEntity importedK8sCluster() {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setId(CLUSTER_ID);
        cluster.setArchType(ClusterArchType.k8s);
        cluster.setManageMode(ManageMode.IMPORTED);
        return cluster;
    }

    private static final class Fixture {

        final ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
        final ClusterRoleUserService roleUserService = mock(ClusterRoleUserService.class);
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final UserInfoEntity user = new UserInfoEntity();
        final K8sTakeoverAccessGuard guard = new K8sTakeoverAccessGuard(
                clusterInfoService, new ClusterAccessGuard(roleUserService, request));

        Fixture(ClusterInfoEntity cluster) {
            when(clusterInfoService.getById(CLUSTER_ID)).thenReturn(cluster);
            when(request.getAttribute(Constants.SESSION_USER)).thenReturn(user);
        }
    }
}
