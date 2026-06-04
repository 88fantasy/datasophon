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
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HiveServer2HandlerStrategy extends ServiceHandlerAbstract implements ServiceRoleStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(HiveServer2HandlerStrategy.class);
    @Override
    public void handler(Integer clusterId, List<String> hosts, String serviceName) {
        CacheUtils.put("enableHiveServer2HA", false);
        if (!hosts.isEmpty()) {
            ProcessUtils.generateClusterVariable(clusterId, serviceName, "masterHiveServer2", hosts.get(0));
        }
        if (hosts.size() > 1) {
            CacheUtils.put("enableHiveServer2HA", true);
        }
    }
    
    @Override
    public void handlerConfig(Integer clusterId, List<ServiceConfig> list, String serviceName) {
        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
        ClusterInfoEntity clusterInfo = ProcessUtils.getClusterInfo(clusterId);
        boolean enableKerberos = false;
        Map<String, ServiceConfig> map = ProcessUtils.translateToMap(list);
        for (ServiceConfig config : list) {
            if ("enableKerberos".equals(config.getName())) {
                enableKerberos = decideEnableKerberos(clusterId, enableKerberos, config, "HIVE");
            }
            
        }
        String key = clusterInfo.getClusterFrame() + Constants.UNDERLINE + "HIVE" + Constants.CONFIG;
        List<ServiceConfig> configs = ServiceConfigMap.get(key);
        ArrayList<ServiceConfig> kbConfigs = new ArrayList<>();
        if (enableKerberos) {
            addConfigWithKerberos(globalVariables, map, configs, kbConfigs);
        } else {
            removeConfigWithKerberos(list, map, configs);
        }
        list.addAll(kbConfigs);
        
    }
    
    @Override
    public void getConfig(Integer clusterId, List<ServiceConfig> list) {
        // if enabled hiveserver2 ha
        ClusterInfoEntity clusterInfo = ProcessUtils.getClusterInfo(clusterId);
        List<ServiceConfig> serviceConfigs =
                ServiceConfigMap.get(clusterInfo.getClusterFrame() + Constants.UNDERLINE + "HIVE" + Constants.CONFIG);
        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
        if ((Boolean) CacheUtils.get("enableHiveServer2HA")) {
            for (ServiceConfig serviceConfig : serviceConfigs) {
                if ("ha".equals(serviceConfig.getConfigType())) {
                    serviceConfig.setRequired(true);
                    serviceConfig.setEnabled(true);
                    serviceConfig.setHidden(false);
                    if (Constants.INPUT.equals(serviceConfig.getType())) {
                        String value = PlaceholderUtils.replacePlaceholders((String) serviceConfig.getValue(),
                                globalVariables, Constants.REGEX_VARIABLE);
                        serviceConfig.setValue(value);
                    }
                    list.add(serviceConfig);
                }
            }
        } else {
            for (ServiceConfig serviceConfig : serviceConfigs) {
                if ("ha".equals(serviceConfig.getConfigType())) {
                    serviceConfig.setRequired(false);
                    serviceConfig.setEnabled(false);
                    serviceConfig.setHidden(true);
                }
            }
        }
    }
    
    @Override
    public void handlerServiceRoleInfo(ServiceRoleInfo serviceRoleInfo, String hostname) {
        if (GlobalVariables.containsValueByServerce(serviceRoleInfo.getClusterId(), serviceRoleInfo.getServiceName(), "masterHiveServer2")
                && !hostname.equals(GlobalVariables.getValueByService(serviceRoleInfo.getClusterId(), serviceRoleInfo.getServiceName(), "masterHiveServer2"))) {
            logger.info("set to slave hiveserver2");
            serviceRoleInfo.setSlave(true);
        }
    }
    
}
