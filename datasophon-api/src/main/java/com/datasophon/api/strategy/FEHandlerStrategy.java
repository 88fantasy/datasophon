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
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.model.ProcInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.OlapUtils;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.AlertLevel;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FEHandlerStrategy implements ServiceRoleStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(FEHandlerStrategy.class);
    
    @Override
    public void handler(Integer clusterId, List<String> hosts, String serviceName) {
        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
        // if feMaster is null, set the first host as feMaster
        // Prevent FE Observer nodes from starting and FE Master nodes from changing
        if (!GlobalVariables.containsValue(clusterId, serviceName + "." + "feMaster")) {
            if (!hosts.isEmpty()) {
                ProcessUtils.generateClusterVariable(clusterId, serviceName, "feMaster", hosts.get(0));
            }
        }
    }
    
    @Override
    public void handlerServiceRoleInfo(ServiceRoleInfo serviceRoleInfo, String hostname) {
        String feMaster = GlobalVariables.getValueByService(serviceRoleInfo.getClusterId(), serviceRoleInfo.getServiceName(), "feMaster");
        if (hostname.equals(feMaster)) {
            logger.info("fe master is {}", feMaster);
            serviceRoleInfo.setSortNum(1);
        } else {
            logger.info("set fe follower master");
            serviceRoleInfo.setMasterHost(feMaster);
            serviceRoleInfo.setSlave(true);
            serviceRoleInfo.setSortNum(2);
        }
        
    }
    
    @Override
    public void handlerServiceRoleCheck(ClusterServiceRoleInstanceEntity roleInstanceEntity,
                                        Map<String, ClusterServiceRoleInstanceEntity> map) {
        String feMaster = GlobalVariables.getValueByService(roleInstanceEntity.getClusterId(), roleInstanceEntity.getServiceName(), "feMaster");
        String rootPassword = GlobalVariables.getValueByService(roleInstanceEntity.getClusterId(), roleInstanceEntity.getServiceName(), "root_password");
        if (roleInstanceEntity.getHostname().equals(feMaster)
                && roleInstanceEntity.getServiceRoleState() == ServiceRoleState.RUNNING) {
            try {
                List<ProcInfo> frontends = OlapUtils.showFrontends(feMaster, rootPassword);
                resolveProcInfoAlert(roleInstanceEntity.getServiceRoleName(), frontends, map);
            } catch (Exception ignored) {
                
            }
            
        }
    }
    
    private void resolveProcInfoAlert(String serviceRoleName, List<ProcInfo> frontends,
                                      Map<String, ClusterServiceRoleInstanceEntity> map) {
        ClusterHostService clusterHostService = SpringTool.getApplicationContext().getBean(ClusterHostService.class);
        for (ProcInfo frontend : frontends) {
            ClusterHostDO clusterHostDO = clusterHostService.getClusterHostByIp(frontend.getIp());
            frontend.setHostName(clusterHostDO.getHostname());
            ClusterServiceRoleInstanceEntity roleInstanceEntity = map.get(frontend.getHostName() + serviceRoleName);
            if (!frontend.getAlive()) {
                String alertTargetName = serviceRoleName + " Not Add To Cluster";
                logger.info("{} at host {} is not add to cluster", serviceRoleName, frontend.getHostName());
                String alertAdvice = "The errmsg is " + frontend.getErrMsg();
                ProcessUtils.saveAlert(roleInstanceEntity, alertTargetName, AlertLevel.WARN, alertAdvice);
            } else {
                ProcessUtils.recoverAlert(roleInstanceEntity);
            }
        }
    }
}
