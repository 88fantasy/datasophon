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


package com.datasophon.api.service.impl;

import com.datasophon.api.enums.Status;
import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.master.handler.service.ServiceConfigureHandler;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterYarnQueueService;
import com.datasophon.common.Constants;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterYarnQueue;
import com.datasophon.dao.mapper.ClusterYarnQueueMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.hutool.core.bean.BeanUtil;

@Service("clusterYarnQueueService")
public class ClusterYarnQueueServiceImpl extends ServiceImpl<ClusterYarnQueueMapper, ClusterYarnQueue>
        implements
            ClusterYarnQueueService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterYarnQueueServiceImpl.class);
    
    @Autowired
    private ClusterServiceRoleInstanceService roleInstanceService;

    @Autowired
    private WorkerCommandClient workerCommandClient;

    @Override
    public Result listByPage(Integer clusterId, Integer page, Integer pageSize) {
        Integer offset = (page - 1) * pageSize;
        List<ClusterYarnQueue> list = this.list(new QueryWrapper<ClusterYarnQueue>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .orderByDesc(Constants.CREATE_TIME)
                .last("limit " + offset + "," + pageSize));
        long count = this.count(new QueryWrapper<ClusterYarnQueue>()
                .eq(Constants.CLUSTER_ID, clusterId));
        for (ClusterYarnQueue clusterYarnQueue : list) {
            String minResources = clusterYarnQueue.getMinCore() + "Core," + clusterYarnQueue.getMinMem() + "GB";
            String maxResources = clusterYarnQueue.getMaxCore() + "Core," + clusterYarnQueue.getMaxMem() + "GB";
            clusterYarnQueue.setMinResources(minResources);
            clusterYarnQueue.setMaxResources(maxResources);
        }
        return Result.success(list).put(Constants.TOTAL, count);
    }
    
    @Override
    public Result refreshQueues(Integer clusterId) throws Exception {
        List<ClusterYarnQueue> list = this.list(new QueryWrapper<ClusterYarnQueue>()
                .eq(Constants.CLUSTER_ID, clusterId));
        // 查询resourcemanager节点
        List<ClusterServiceRoleInstanceEntity> roleList =
                roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, "ResourceManager");
        
        // 构建configfilemap
        HashMap<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
        Generators generators = new Generators();
        generators.setFilename("fair-scheduler.xml");
        generators.setOutputDirectory("etc/hadoop");
        generators.setConfigFormat("custom");
        generators.setTemplateName("fair-scheduler.ftl");
        
        ArrayList<ServiceConfig> serviceConfigs = new ArrayList<>();
        ServiceConfig config = new ServiceConfig();
        ArrayList<JSONObject> queueList = new ArrayList<>();
        for (ClusterYarnQueue clusterYarnQueue : list) {
            JSONObject queue = new JSONObject();
            Integer minMem = clusterYarnQueue.getMinMem() * 1024;
            Integer maxMem = clusterYarnQueue.getMaxMem() * 1024;
            clusterYarnQueue.setMinResources(minMem + "mb," + clusterYarnQueue.getMinCore() + "vcores");
            clusterYarnQueue.setMaxResources(maxMem + "mb," + clusterYarnQueue.getMaxCore() + "vcores");
            BeanUtil.copyProperties(clusterYarnQueue, queue, false);
            queueList.add(queue);
        }
        config.setName("queueList");
        config.setValue(queueList);
        config.setConfigType("map");
        config.setRequired(true);
        config.setEnabled(true);
        serviceConfigs.add(config);
        
        configFileMap.put(generators, serviceConfigs);
        String hostname = "";
        for (ClusterServiceRoleInstanceEntity roleInstanceEntity : roleList) {
            // 调用指令刷新yarn队列配置
            ServiceRoleInfo serviceRoleInfo = new ServiceRoleInfo();
            serviceRoleInfo.setName("ResourceManager");
            serviceRoleInfo.setParentName("YARN");
            serviceRoleInfo.setConfigFileMap(configFileMap);
            serviceRoleInfo.setDecompressPackageName("hadoop");
            serviceRoleInfo.setHostname(roleInstanceEntity.getHostname());
            ServiceConfigureHandler configureHandler = new ServiceConfigureHandler();
            ExecResult execResult = configureHandler.handlerRequest(serviceRoleInfo);
            if (!execResult.getExecResult()) {
                return Result.error(Status.FAILED_REFRESH_THE_QUEUE_TO_YARN.getMsg());
            }
            if (StringUtils.isBlank(hostname)) {
                hostname = roleInstanceEntity.getHostname();
            }
        }
        ArrayList<String> commands = new ArrayList<>();
        commands.add(Constants.INSTALL_PATH + "/hadoop/bin/yarn");
        commands.add("rmadmin");
        commands.add("-refreshQueues");
        ExecResult execResult = workerCommandClient.executeCmd(hostname, commands);
        if (execResult.getExecResult()) {
            logger.info("yarn dfsadmin -refreshQueues success at {}", hostname);
        } else {
            logger.info(execResult.getExecOut());
            return Result.error(Status.FAILED_REFRESH_THE_QUEUE_TO_YARN.getMsg());
        }
        return Result.success();
    }
}
