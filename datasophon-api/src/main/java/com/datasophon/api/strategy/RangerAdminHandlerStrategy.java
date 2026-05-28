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
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

public class RangerAdminHandlerStrategy extends ServiceHandlerAbstract implements ServiceRoleStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(RangerAdminHandlerStrategy.class);
    
    @Override
    public void handler(Integer clusterId, List<String> hosts, String serviceName) {
        if (!hosts.isEmpty()) {
            String rangerAdminUrl = "http://" + hosts.get(0) + ":6080";
            logger.info("rangerAdminUrl is {}", rangerAdminUrl);
            ProcessUtils.generateClusterVariable(clusterId, serviceName, "rangerAdminUrl",
                    rangerAdminUrl);
        }
    }
    
    @Override
    public void handlerConfig(Integer clusterId, List<ServiceConfig> list, String serviceName) {
        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
        ClusterInfoEntity clusterInfo = ProcessUtils.getClusterInfo(clusterId);
        boolean enableKerberos = false;
        Map<String, ServiceConfig> map = ProcessUtils.translateToMap(list);
        // enable ranger plugin
        for (ServiceConfig config : list) {
            if ("enableHDFSPlugin".equals(config.getName()) && (Boolean) config.getValue()) {
                logger.info("enableHdfsPlugin");
                ProcessUtils.generateClusterVariable(clusterId, serviceName, "enableHDFSPlugin",
                        "true");
                enableRangerPlugin(clusterId, "HDFS", "NameNode");
            }
            if ("enableHIVEPlugin".equals(config.getName()) && (Boolean) config.getValue()) {
                logger.info("enableHivePlugin");
                ProcessUtils.generateClusterVariable(clusterId, serviceName, "enableHIVEPlugin",
                        "true");
                enableRangerPlugin(clusterId, "HIVE", "HiveServer2");
            }
            if ("enableHBASEPlugin".equals(config.getName()) && (Boolean) config.getValue()) {
                logger.info("enableHbasePlugin");
                ProcessUtils.generateClusterVariable(clusterId, serviceName, "enableHBASEPlugin",
                        "true");
                enableRangerPlugin(clusterId, "HBASE", "HbaseMaster");
            }
            if (config.getName().contains("Plugin") && !(Boolean) config.getValue()) {
                String configName = config.getName();
                ProcessUtils.generateClusterVariable(clusterId, serviceName, configName,"false");
            }
            if ("enableKerberos".equals(config.getName())) {
                enableKerberos = decideEnableKerberos(clusterId, enableKerberos, config, "RANGER");
            }
        }
        String key = clusterInfo.getClusterFrame() + Constants.UNDERLINE + "RANGER" + Constants.CONFIG;
        List<ServiceConfig> configs = ServiceConfigMap.get(key);
        ArrayList<ServiceConfig> kbConfigs = new ArrayList<>();
        if (enableKerberos) {
            addConfigWithKerberos(globalVariables, map, configs, kbConfigs);
        } else {
            removeConfigWithKerberos(list, map, configs);
        }
        list.addAll(kbConfigs);
    }
    
    private void enableRangerPlugin(Integer clusterId, String serviceName, String serviceRoleName) {
        ClusterServiceInstanceService serviceInstanceService =
                SpringTool.getApplicationContext().getBean(ClusterServiceInstanceService.class);
        ClusterServiceRoleInstanceService roleInstanceService =
                SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceService.class);
        ClusterServiceRoleGroupConfigService roleGroupConfigService =
                SpringTool.getApplicationContext().getBean(ClusterServiceRoleGroupConfigService.class);
        ClusterInfoService clusterInfoService = SpringTool.getApplicationContext().getBean(ClusterInfoService.class);
        ServiceInstallService serviceInstallService =
                SpringTool.getApplicationContext().getBean(ServiceInstallService.class);
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        String rangerAdminUrl = GlobalVariables.getValueByService(clusterId,serviceName, "rangerAdminUrl");
        ClusterServiceInstanceEntity serviceInstance =
                serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(clusterId, serviceName);
        // 查询角色组id
        List<ClusterServiceRoleInstanceEntity> roleList =
                roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, serviceRoleName);
        
        if (Objects.nonNull(roleList) && !roleList.isEmpty()) {
            Integer roleGroupId = roleList.get(0).getRoleGroupId();
            
            ClusterServiceRoleGroupConfig config = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
            List<ServiceConfig> serviceConfigs = JSON.parseArray(config.getConfigJson(), ServiceConfig.class);
            Map<String, ServiceConfig> map = serviceConfigs.stream()
                    .collect(Collectors.toMap(ServiceConfig::getName, serviceConfig -> serviceConfig, (v1, v2) -> v1));
            
            String key = clusterInfo.getClusterFrame() + Constants.UNDERLINE + serviceName + Constants.CONFIG;
            List<ServiceConfig> configs = ServiceConfigMap.get(key);
            for (ServiceConfig parameter : configs) {
                String name = parameter.getName();
                if (map.containsKey(name)) {
                    parameter = map.get(name);
                }
                
                if ("permission".equals(parameter.getConfigType())) {
                    parameter.setHidden(false);
                    parameter.setRequired(true);
                    parameter.setEnabled(true);
                }
                if ("dfs.permissions.enabled".equals(parameter.getName())) {
                    parameter.setHidden(false);
                    parameter.setRequired(true);
                    parameter.setEnabled(true);
                    parameter.setValue(true);
                    
                }
                if ("rangerAdminUrl".equals(parameter.getName())) {
                    parameter.setHidden(false);
                    parameter.setRequired(true);
                    parameter.setEnabled(true);
                    parameter.setValue(rangerAdminUrl);
                }
                if (!map.containsKey(name)) {
                    logger.info("put config {} into service {}", name, serviceRoleName);
                    serviceConfigs.add(parameter);
                }
            }
            logger.info("Update hdfs enable ranger plugin");
            serviceInstallService.saveServiceConfig(clusterId, serviceInstance.getServiceName(), serviceConfigs,
                    roleGroupId);
        }
    }
}
