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


package com.datasophon.api.strategy;

import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.dao.entity.ClusterHostDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class EtcdHandlerStrategy implements ServiceRoleStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(EtcdHandlerStrategy.class);
    
    @Override
    public void handler(Integer clusterId, List<String> hosts, String serviceName) {
        if (!hosts.isEmpty()) {
            // 保存etcdNodeList到全局变量
            ClusterHostService clusterHostService = SpringTool.getApplicationContext().getBean(ClusterHostService.class);
            List<ClusterHostDO> hs = clusterHostService.lambdaQuery().in(ClusterHostDO::getHostname, hosts).list();
            // initial-cluster
            String etcdNodeList = hs.stream().map(s -> s.getHostname() + "=http://" + s.getIp() + ":2380").collect(Collectors.joining(","));
            ProcessUtils.generateClusterVariable(clusterId, serviceName, "etcd-node-list", etcdNodeList);

            // advertise-client-urls
            String advertiseClientUrls = hs.stream().map(s -> "http://" + s.getIp() + ":2379").collect(Collectors.joining(","));
            ProcessUtils.generateClusterVariable(clusterId, serviceName, "etcd-advertise-client-urls", advertiseClientUrls);
        }
    }

}
