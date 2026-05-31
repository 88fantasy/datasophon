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
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NameNodeHandlerStrategy extends ServiceHandlerAbstract implements ServiceRoleStrategy {

    private static final Logger logger = LoggerFactory.getLogger(NameNodeHandlerStrategy.class);

    private static final String ENABLE_RACK = "enableRack";

    private static final String ENABLE_KERBEROS = "enableKerberos";

    private static final String ACTIVE = "active";

    @Override
    public void handler(Integer clusterId, List<String> hosts, String serviceName) {
        ProcessUtils.generateClusterVariable(clusterId, serviceName, "nn1", hosts.get(0));
        ProcessUtils.generateClusterVariable(clusterId, serviceName, "nn2", hosts.get(1));
    }

    @Override
    public void handlerConfig(Integer clusterId, List<ServiceConfig> list, String serviceName) {
        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
        ClusterInfoEntity clusterInfo = ProcessUtils.getClusterInfo(clusterId);

        boolean enableRack = false;
        boolean enableKerberos = false;
        Map<String, ServiceConfig> map = ProcessUtils.translateToMap(list);

        String key = clusterInfo.getClusterFrame() + Constants.UNDERLINE + "HDFS" + Constants.CONFIG;
        List<ServiceConfig> configs = ServiceConfigMap.get(key);

        for (ServiceConfig config : list) {
            if (ENABLE_RACK.equals(config.getName())) {
                enableRack = isEnableRack(config, enableRack);
            }
            if (ENABLE_KERBEROS.equals(config.getName())) {
                enableKerberos = decideEnableKerberos(clusterId, enableKerberos, config, "HDFS");
            }
        }
        List<ServiceConfig> rackConfigs = new ArrayList<>();
        if (enableRack) {
            logger.info("start to add rack config");
            addConfigWithRack(globalVariables, map, configs, rackConfigs);
        } else {
            removeConfigWithRack(list, map, configs);
        }
        list.addAll(rackConfigs);

        ArrayList<ServiceConfig> kbConfigs = new ArrayList<>();
        if (enableKerberos) {
            addConfigWithKerberos(globalVariables, map, configs, kbConfigs);
        } else {
            removeConfigWithKerberos(list, map, configs);
        }
        list.addAll(kbConfigs);
    }

    @Override
    public void handlerServiceRoleInfo(ServiceRoleInfo serviceRoleInfo, String hostname) {
        String nn2 = GlobalVariables.getValueByService(serviceRoleInfo.getClusterId(), serviceRoleInfo.getServiceName(), "nn2");
        if (hostname.equals(nn2)) {
            logger.info("set to slave namenode");
            serviceRoleInfo.setSlave(true);
            serviceRoleInfo.setSortNum(5);
        }
    }

    @Override
    public void handlerServiceRoleCheck(
            ClusterServiceRoleInstanceEntity roleInstanceEntity,
            Map<String, ClusterServiceRoleInstanceEntity> map) {
        String nn2 = GlobalVariables.getValueByService(roleInstanceEntity.getClusterId(), roleInstanceEntity.getServiceName(), "nn2");
//    TODO 使用 {ROOT.XXServiceName.INSTALL_PATH}
        String hadoopHome = GlobalVariables.getValue(roleInstanceEntity.getClusterId(), "HADOOP_HOME");
        String commandLine = hadoopHome + "/bin/hdfs haadmin -getServiceState nn1";
        if (roleInstanceEntity.getHostname().equals(nn2)) {
            commandLine = hadoopHome + "/bin/hdfs haadmin -getServiceState nn2";
        }
        getNMState(roleInstanceEntity, commandLine);
    }

    private void getNMState(ClusterServiceRoleInstanceEntity roleInstanceEntity, String commandLine) {
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
            logger.error(e.getMessage());
        }
    }
}
