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

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.load.ServiceConfigMap;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.HostUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

public class ZkServerHandlerStrategy implements ServiceRoleStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(ZkServerHandlerStrategy.class);
    
    @Override
    public void handler(Integer clusterId, List<String> hosts, String serviceName) {
        // 保存zkUrls到全局变量
        String join = String.join(":2181,", hosts);
        String zkUrls = join + ":2181";
        ProcessUtils.generateClusterVariable(clusterId, serviceName, "zkUrls", zkUrls);
        // 保存hbaseZkUrls到全局变量
        String hbaseZkUrls = String.join(",", hosts);
        ProcessUtils.generateClusterVariable(clusterId, serviceName, "zkHostsUrl", hbaseZkUrls);
    }
    
    @Override
    public void handlerConfig(Integer clusterId, List<ServiceConfig> list, String serviceName) {
        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
        ClusterInfoEntity clusterInfo = ProcessUtils.getClusterInfo(clusterId);
        boolean enableKerberos = false;
        Map<String, ServiceConfig> map = ProcessUtils.translateToMap(list);
        
        for (ServiceConfig config : list) {
            if ("enableKerberos".equals(config.getName())) {
                if ((Boolean) config.getValue()) {
                    enableKerberos = true;
                    ProcessUtils.generateClusterVariable(clusterId, serviceName, "enableZOOKEEPERKerberos", "true");
                } else {
                    ProcessUtils.generateClusterVariable(clusterId, serviceName, "enableZOOKEEPERKerberos", "false");
                }
            }
        }
        
        String key = clusterInfo.getClusterFrame() + Constants.UNDERLINE + "ZOOKEEPER" + Constants.CONFIG;
        List<ServiceConfig> configs = ServiceConfigMap.get(key);
        ArrayList<ServiceConfig> kbConfigs = new ArrayList<>();
        if (enableKerberos) {
            for (ServiceConfig serviceConfig : configs) {
                if (serviceConfig.isConfigWithKerberos()) {
                    if (map.containsKey(serviceConfig.getName())) {
                        ServiceConfig config = map.get(serviceConfig.getName());
                        config.setRequired(true);
                        config.setEnabled(true);
                        config.setHidden(false);
                        String value = PlaceholderUtils.replacePlaceholders((String) serviceConfig.getValue(),
                                globalVariables, Constants.REGEX_VARIABLE);
                        logger.info("the value is {}", value);
                        config.setValue(value);
                    } else {
                        serviceConfig.setRequired(true);
                        serviceConfig.setEnabled(true);
                        serviceConfig.setHidden(false);
                        String value = PlaceholderUtils.replacePlaceholders((String) serviceConfig.getValue(),
                                globalVariables, Constants.REGEX_VARIABLE);
                        serviceConfig.setValue(value);
                        kbConfigs.add(serviceConfig);
                    }
                }
            }
        } else {
            for (ServiceConfig serviceConfig : configs) {
                if (serviceConfig.isConfigWithKerberos()) {
                    if (map.containsKey(serviceConfig.getName())) {
                        list.remove(map.get(serviceConfig.getName()));
                    }
                }
            }
        }
        list.addAll(kbConfigs);
    }
    
    /**
     *
     */
    @Override
    public void getConfig(Integer clusterId, List<ServiceConfig> list) {
        // add server.x config
        ClusterInfoService clusterInfoService = SpringTool.getApplicationContext().getBean(ClusterInfoService.class);
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        
        String hostMapKey = clusterInfo.getClusterCode() + Constants.UNDERLINE + Constants.SERVICE_ROLE_HOST_MAPPING;
        @SuppressWarnings("unchecked")
        HashMap<String, List<String>> hostMap = (HashMap<String, List<String>>) CacheUtils.get(hostMapKey);
        
        if (Objects.nonNull(hostMap)) {
            List<String> zkServers = hostMap.get("ZkServer");
            
            Map<String, ServiceConfig> map = ProcessUtils.translateToMap(list);
            
            Integer myid = 1;
            for (String server : zkServers) {
                ServiceConfig serviceConfig = new ServiceConfig();
                serviceConfig.setName("server." + myid);
                serviceConfig.setLabel("server." + myid);
                serviceConfig.setValue(HostUtils.getIp(server) + ":2888:3888");
                serviceConfig.setHidden(false);
                serviceConfig.setRequired(true);
                serviceConfig.setEnabled(true);
                serviceConfig.setType("input");
                serviceConfig.setDefaultValue("");
                serviceConfig.setConfigType("zkserver");
                if (map.containsKey("server." + myid)) {
                    logger.info("set zk server {}", myid);
                    ServiceConfig config = map.get("server." + myid);
                    BeanUtils.copyProperties(serviceConfig, config);
                } else {
                    logger.info("add zk server.x config");
                    list.add(serviceConfig);
                }
                CacheUtils.put("zkserver_" + server, myid);
                myid++;
            }
        }
    }
    
}
