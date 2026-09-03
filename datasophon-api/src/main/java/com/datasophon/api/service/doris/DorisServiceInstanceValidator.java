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

import com.datasophon.api.enums.Status;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** 校验活动任务查询的服务实例确实属于请求集群且为 Doris。 */
@Component
public class DorisServiceInstanceValidator {

    private final ClusterServiceInstanceService serviceInstanceService;

    public DorisServiceInstanceValidator(ClusterServiceInstanceService serviceInstanceService) {
        this.serviceInstanceService = serviceInstanceService;
    }

    public ClusterServiceInstanceEntity requireDorisInstance(Integer clusterId, Integer instanceId) {
        ClusterServiceInstanceEntity instance = instanceId == null
                ? null
                : serviceInstanceService.getById(instanceId);
        if (clusterId == null || instance == null || !clusterId.equals(instance.getClusterId())
                || !"DORIS".equalsIgnoreCase(instance.getServiceName())) {
            throw new ResponseStatusException(BAD_REQUEST, Status.INSTANCE_MISMATCH.getMsg());
        }
        return instance;
    }
}
