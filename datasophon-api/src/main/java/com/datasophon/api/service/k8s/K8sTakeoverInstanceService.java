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

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.k8s.InstanceSource;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 接管实例的取消登记与只读反查。
 *
 * <p>两个方法都严格不向目标集群写入：取消接管只删 Datasophon 自己的登记行，
 * 反查配置走 {@code helm get values}（只读命令）。
 */
@Service
public class K8sTakeoverInstanceService {

    private final K8sServiceInstanceService k8sServiceInstanceService;
    private final K8sServiceInstanceValuesService k8sServiceInstanceValuesService;
    private final K8sClusterConfigService k8sClusterConfigService;
    private final HelmReleaseReader helmReleaseReader;

    public K8sTakeoverInstanceService(K8sServiceInstanceService k8sServiceInstanceService,
                                      K8sServiceInstanceValuesService k8sServiceInstanceValuesService,
                                      K8sClusterConfigService k8sClusterConfigService,
                                      HelmReleaseReader helmReleaseReader) {
        this.k8sServiceInstanceService = k8sServiceInstanceService;
        this.k8sServiceInstanceValuesService = k8sServiceInstanceValuesService;
        this.k8sClusterConfigService = k8sClusterConfigService;
        this.helmReleaseReader = helmReleaseReader;
    }

    /**
     * 取消接管：只删除 Datasophon 的登记记录，集群内的 release 原样保留。
     *
     * <p>刻意不复用 {@code K8sServiceInstanceService.removeInstanceId}——后者会调
     * {@code uninstallRelease} 把 release 从目标集群卸载掉，对接管实例是灾难性的。
     *
     * @param clusterId  集群 ID
     * @param instanceId 实例 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelTakeover(Integer clusterId, Integer instanceId) {
        K8sServiceInstanceVO instance = requireImportedInstance(clusterId, instanceId);
        k8sServiceInstanceValuesService.removeByInstanceId(instance.getId());
        k8sServiceInstanceService.removeById(instance.getId());
    }

    /**
     * 读取接管实例对应 release 的 user-supplied values（只读）。
     *
     * @param clusterId  集群 ID
     * @param instanceId 实例 ID
     * @return values 的 JSON 文本
     */
    public String readValues(Integer clusterId, Integer instanceId) {
        K8sServiceInstanceVO instance = requireImportedInstance(clusterId, instanceId);
        if (instance.getReleaseName() == null || instance.getReleaseName().isBlank()) {
            throw new BusinessHintException("该接管实例未登记 release 名，无法反查配置");
        }
        K8sClusterConfig config = k8sClusterConfigService.getByClusterId(clusterId);
        if (config == null) {
            throw new BusinessHintException("集群未配置 K8s 连接信息");
        }
        return helmReleaseReader.getValues(config, instance.getReleaseName(), instance.getNamespace());
    }

    private K8sServiceInstanceVO requireImportedInstance(Integer clusterId, Integer instanceId) {
        K8sServiceInstanceVO instance = k8sServiceInstanceService.getVoById(instanceId)
                .orElseThrow(() -> new BusinessHintException("服务实例不存在"));
        if (!Objects.equals(clusterId, instance.getClusterId())) {
            throw new BusinessHintException("服务实例不属于该集群");
        }
        if (!InstanceSource.IMPORTED.name().equals(instance.getSource())) {
            throw new BusinessHintException("该服务实例由平台安装，不适用接管相关操作");
        }
        return instance;
    }
}
