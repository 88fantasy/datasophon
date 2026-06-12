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

import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.load.ServiceConfigMap;
import com.datasophon.api.service.ClusterServiceRoleInstanceWebuisService;
import com.datasophon.api.service.ClusterYarnSchedulerService;
import com.datasophon.api.utils.ServiceConfigUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterYarnScheduler;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RMHandlerStrategy extends ServiceHandlerAbstract implements ServiceRoleStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(RMHandlerStrategy.class);
    
    private static final String ACTIVE = "active";
    
    @Override
    public void handler(Integer clusterId, List<String> hosts, String serviceName) {
        ServiceConfigUtils.generateClusterVariable(clusterId, serviceName, "rm1", hosts.get(0));
        ServiceConfigUtils.generateClusterVariable(clusterId, serviceName, "rm2", hosts.get(1));
        ServiceConfigUtils.generateClusterVariable(clusterId, serviceName, "rmHost", String.join(",", hosts));
    }
    
    @Override
    public void handlerConfig(Integer clusterId, List<ServiceConfig> list, String serviceName) {
        ClusterYarnSchedulerService schedulerService =
                SpringTool.getApplicationContext().getBean(ClusterYarnSchedulerService.class);
        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
        ClusterInfoEntity clusterInfo = ServiceConfigUtils.getClusterInfo(clusterId);
        boolean enableKerberos = false;
        Map<String, ServiceConfig> map = ServiceConfigUtils.translateToMap(list);
        for (ServiceConfig config : list) {
            if ("yarn.resourcemanager.scheduler.class".equals(config.getName())) {
                ClusterYarnScheduler scheduler = schedulerService.getScheduler(clusterId);
                if ("org.apache.hadoop.yarn.server.resourcemanager.scheduler.fair.FairScheduler".equals(config.getValue())) {
                    if ("capacity".equals(scheduler.getScheduler())) {
                        scheduler.setScheduler("fair");
                        schedulerService.updateById(scheduler);
                    }
                } else {
                    if ("fair".equals(scheduler.getScheduler())) {
                        scheduler.setScheduler("capacity");
                        schedulerService.updateById(scheduler);
                    }
                }
            }
            if ("enableKerberos".equals(config.getName())) {
                enableKerberos = decideEnableKerberos(clusterId, enableKerberos, config, "YARN");
            }
        }
        String key = clusterInfo.getClusterFrame() + Constants.UNDERLINE + "YARN" + Constants.CONFIG;
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
    public void handlerServiceRoleCheck(ClusterServiceRoleInstanceEntity roleInstanceEntity,
                                        Map<String, ClusterServiceRoleInstanceEntity> map) {
        Integer clusterId = roleInstanceEntity.getClusterId();
        String commandLine;
        String yarnAclAdminUser = GlobalVariables.getValueByService(clusterId, roleInstanceEntity.getServiceName(), "yarn.admin.acl");
        String rm2 = GlobalVariables.getValueByService(clusterId, roleInstanceEntity.getServiceName(), "rm2");
        
        // TODO 使用 {ROOT.XXServiceName.xx}。HADOOP_HOME使用比较多
        String hadoopHome = GlobalVariables.getValue(clusterId, "HADOOP_HOME");
        String curRm = roleInstanceEntity.getHostname().equals(rm2) ? "rm2" : "rm1";
        
        if (StringUtils.isNotEmpty(yarnAclAdminUser)) {
            commandLine = String.format("sudo -u %s %s/bin/yarn rmadmin -getServiceState %s",
                    yarnAclAdminUser, hadoopHome, curRm);
        } else {
            commandLine = String.format("%s/bin/yarn rmadmin -getServiceState %s",
                    hadoopHome, curRm);
        }
        getRMState(roleInstanceEntity, commandLine);
    }
    
    private void getRMState(ClusterServiceRoleInstanceEntity roleInstanceEntity, String commandLine) {
        ClusterServiceRoleInstanceWebuisService webuisService =
                SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceWebuisService.class);
        try {
            WorkerCommandClient workerCommandClient =
                    SpringTool.getApplicationContext().getBean(WorkerCommandClient.class);
            ExecResult execResult = workerCommandClient.executeCmdLine(roleInstanceEntity.getHostname(), commandLine);
            if (execResult.getExecResult()) {
                if (execResult.getExecOut().contains(ACTIVE)) {
                    webuisService.updateWebUiToActive(roleInstanceEntity.getId());
                } else {
                    webuisService.updateWebUiToStandby(roleInstanceEntity.getId());
                }
            } else {
                webuisService.updateWebUiToStandby(roleInstanceEntity.getId());
            }
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }
}
