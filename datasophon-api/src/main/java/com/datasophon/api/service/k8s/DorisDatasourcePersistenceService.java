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

package com.datasophon.api.service.k8s;

import com.datasophon.api.observability.OtelCredentialService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在一个事务内保存 Doris 地址和只读账号密码。 */
@Service
public class DorisDatasourcePersistenceService {

    private final K8sClusterConfigService k8sClusterConfigService;
    private final ClusterVariableService clusterVariableService;

    public DorisDatasourcePersistenceService(K8sClusterConfigService k8sClusterConfigService,
                                             ClusterVariableService clusterVariableService) {
        this.k8sClusterConfigService = k8sClusterConfigService;
        this.clusterVariableService = clusterVariableService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(K8sClusterConfig config, String password) {
        k8sClusterConfigService.updateById(config);
        ClusterVariable existing = clusterVariableService.getVariableByVariableName(
                config.getClusterId(), OtelCredentialService.DORIS_SERVICE_NAME,
                OtelCredentialService.DORIS_READER_PASSWORD);
        if (existing != null) {
            existing.setVariableValue(password);
            clusterVariableService.updateById(existing);
            return;
        }
        ClusterVariable variable = new ClusterVariable();
        variable.setClusterId(config.getClusterId());
        variable.setServiceName(OtelCredentialService.DORIS_SERVICE_NAME);
        variable.setVariableName(OtelCredentialService.DORIS_READER_PASSWORD);
        variable.setVariableValue(password);
        clusterVariableService.save(variable);
    }
}
