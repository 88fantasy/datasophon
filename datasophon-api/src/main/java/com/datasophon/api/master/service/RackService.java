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


package com.datasophon.api.master.service;

import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.datasophon.api.master.handler.service.ServiceConfigureHandler;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.PackageUtils;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.common.command.GenerateRackPropCommand;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Rack 属性文件生成 Spring Service，业务逻辑来自 {@link RackActor}。
 */
@Service
public class RackService {

    private static final Logger logger = LoggerFactory.getLogger(RackService.class);

    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final ClusterHostService hostService;
    private final ClusterInfoService clusterInfoService;

    public RackService(ClusterServiceRoleInstanceService roleInstanceService,
                       ClusterHostService hostService,
                       ClusterInfoService clusterInfoService) {
        this.roleInstanceService = roleInstanceService;
        this.hostService = hostService;
        this.clusterInfoService = clusterInfoService;
    }

    /**
     * 异步生成 rack.properties 并推送到所有 NameNode（替代 RackActor.tell(command)）。
     */
    @Async("masterExecutor")
    public void generateRackProp(GenerateRackPropCommand command) {
        List<ClusterServiceRoleInstanceEntity> roleList = roleInstanceService
                .getServiceRoleInstanceListByClusterIdAndRoleName(command.getClusterId(), "NameNode");
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(command.getClusterId());

        Generators generators = new Generators();
        generators.setFilename("rack.properties");
        generators.setOutputDirectory("etc/hadoop");
        generators.setConfigFormat("properties2");

        ArrayList<ServiceConfig> serviceConfigs = new ArrayList<>();
        List<ClusterHostDO> hostList = hostService.list();
        for (ClusterHostDO host : hostList) {
            ServiceConfig sc = ProcessUtils.createServiceConfig(
                    host.getIp(), Constants.SLASH + host.getRack(), "input");
            serviceConfigs.add(sc);
        }

        HashMap<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
        configFileMap.put(generators, serviceConfigs);

        for (ClusterServiceRoleInstanceEntity roleInstance : roleList) {
            ServiceRoleInfo serviceRoleInfo = new ServiceRoleInfo();
            serviceRoleInfo.setName("NameNode");
            serviceRoleInfo.setParentName("HDFS");
            serviceRoleInfo.setConfigFileMap(configFileMap);
            serviceRoleInfo.setDecompressPackageName(
                    PackageUtils.getServiceDcPackageName(clusterInfo.getClusterFrame(), "HDFS"));
            serviceRoleInfo.setHostname(roleInstance.getHostname());
            try {
                ExecResult result = new ServiceConfigureHandler().handlerRequest(serviceRoleInfo);
                if (!result.getExecResult()) {
                    logger.error("generate rack.properties failed for host {}", roleInstance.getHostname());
                }
            } catch (Exception e) {
                logger.error("generate rack.properties failed for host {}: {}", roleInstance.getHostname(), e.getMessage(), e);
            }
        }
    }
}
