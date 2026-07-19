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
import com.datasophon.api.utils.ServiceAlertUtils;
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
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BEHandlerStrategy implements ServiceRoleStrategy {

    private static final Logger logger = LoggerFactory.getLogger(BEHandlerStrategy.class);

    @Override
    public void handlerServiceRoleInfo(ServiceRoleInfo serviceRoleInfo, String hostname) {
        Map<String, String> globalVariables = GlobalVariables.getVariables(serviceRoleInfo.getClusterId());
        String feMaster = globalVariables.get("${DORIS.DorisFE.__hostIp__}");
        logger.info("fe master is {}", feMaster);
        serviceRoleInfo.setMasterHost(feMaster);
    }

    @Override
    public void handlerServiceRoleCheck(ClusterServiceRoleInstanceEntity roleInstanceEntity,
                                        Map<String, ClusterServiceRoleInstanceEntity> map) {
        Map<String, String> globalVariables = GlobalVariables.getVariables(roleInstanceEntity.getClusterId());
        String feMaster = globalVariables.get("${DORIS.DorisFE.__hostIp__}");
        String rootPassword = globalVariables.get("${DORIS.root_password}");

        if (roleInstanceEntity.getServiceRoleState() == ServiceRoleState.RUNNING) {
            try {
                List<ProcInfo> backends = OlapUtils.showBackends(feMaster, rootPassword);
                resolveProcInfoAlert(roleInstanceEntity.getServiceRoleName(), backends, map);
            } catch (Exception e) {
                logger.error("dorisBE service role check error. fe:{}", feMaster, e);
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
            if (Objects.isNull(roleInstanceEntity)) {
                logger.warn("{} at host {} is not add to cluster", serviceRoleName, frontend.getHostName());
                return;
            }
            if (!frontend.getAlive()) {
                String alertTargetName = serviceRoleName + " Not Add To Cluster";
                logger.info("{} at host {} is not add to cluster", serviceRoleName, frontend.getHostName());
                String alertAdvice = "The errmsg is " + frontend.getErrMsg();
                ServiceAlertUtils.saveAlert(roleInstanceEntity, alertTargetName, AlertLevel.WARN, alertAdvice);
            } else {
                ServiceAlertUtils.recoverAlert(roleInstanceEntity);
            }
        }
    }
}
