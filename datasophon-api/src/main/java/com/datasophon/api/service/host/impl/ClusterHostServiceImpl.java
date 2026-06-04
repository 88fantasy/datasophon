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

package com.datasophon.api.service.host.impl;

import com.datasophon.api.enums.Status;
import com.datasophon.api.master.service.PrometheusService;
import com.datasophon.api.master.service.RackService;
import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.service.ClusterRackService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.service.host.dto.QueryHostListPageDTO;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.ExecuteCmdCommand;
import com.datasophon.common.command.GenerateHostPrometheusConfig;
import com.datasophon.common.command.GenerateRackPropCommand;
import com.datasophon.common.model.HostInfo;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterRack;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.RoleType;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.dao.mapper.ClusterHostMapper;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import com.datasophon.dao.mapper.ClusterServiceRoleInstanceMapper;
import com.datasophon.domain.host.enums.HostState;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.hutool.crypto.SecureUtil;

@Service("clusterHostService")
@Transactional
public class ClusterHostServiceImpl extends ServiceImpl<ClusterHostMapper, ClusterHostDO>
        implements
            ClusterHostService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterHostServiceImpl.class);
    
    @Autowired
    ClusterHostMapper hostMapper;
    
    @Autowired
    ClusterServiceRoleInstanceMapper roleInstanceMapper;
    
    @Autowired
    ClusterInfoMapper clusterInfoMapper;
    
    @Autowired
    ClusterRackService clusterRackService;
    
    @Lazy
    @Autowired
    PrometheusService prometheusService;
    
    @Autowired
    RackService rackService;
    
    @Autowired
    WorkerCallAdapter workerCallAdapter;
    
    private final String ip = "ip";
    
    @Override
    public ClusterHostDO getClusterHostByHostname(String hostname) {
        return hostMapper.getClusterHostByHostname(hostname);
    }
    
    @Override
    public ClusterHostDO getClusterHostByIp(String ip) {
        return hostMapper.getClusterHostByIp(ip);
    }
    
    @Override
    public Result listByPage(Integer clusterId, String hostname, String ip, String cpuArchitecture, Integer hostState,
                             String orderField, String orderType, Integer page, Integer pageSize) {
        List<QueryHostListPageDTO> hostListPageDTOS = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        List<ClusterHostDO> list =
                this.list(new QueryWrapper<ClusterHostDO>().eq(Constants.CLUSTER_ID, clusterId)
                        .eq(Constants.MANAGED, 1)
                        .eq(StringUtils.isNotBlank(cpuArchitecture), Constants.CPU_ARCHITECTURE, cpuArchitecture)
                        .eq(hostState != null, Constants.HOST_STATE, hostState)
                        .like(StringUtils.isNotBlank(ip), this.ip, ip)
                        .like(StringUtils.isNotBlank(hostname), Constants.HOSTNAME, hostname)
                        .orderByAsc("asc".equals(orderType), orderField)
                        .orderByDesc("desc".equals(orderType), orderField)
                        .last("limit " + offset + "," + pageSize));
        
        // 回显rack的名称 而不是ID
        Map<String, String> rackMap = clusterRackService.queryClusterRack(clusterId).stream()
                .collect(Collectors.toMap(obj -> obj.getId() + "", ClusterRack::getRack));
        for (ClusterHostDO clusterHostDO : list) {
            QueryHostListPageDTO queryHostListPageDTO = new QueryHostListPageDTO();
            BeanUtils.copyProperties(clusterHostDO, queryHostListPageDTO);
            // 查询主机上服务角色数
            long serviceRoleNum = roleInstanceMapper.selectCount(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                    .eq(Constants.HOSTNAME, clusterHostDO.getHostname()));
            queryHostListPageDTO.setServiceRoleNum((int) serviceRoleNum);
            queryHostListPageDTO.setHostState(clusterHostDO.getHostState().getValue());
            queryHostListPageDTO.setRack(rackMap.getOrDefault(queryHostListPageDTO.getRack(), "/default-rack"));
            hostListPageDTOS.add(queryHostListPageDTO);
        }
        long count = this.count(new QueryWrapper<ClusterHostDO>().eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.MANAGED, 1)
                .eq(StringUtils.isNotBlank(cpuArchitecture), Constants.CPU_ARCHITECTURE, cpuArchitecture)
                .eq(hostState != null, Constants.HOST_STATE, hostState)
                .like(StringUtils.isNotBlank(hostname), Constants.HOSTNAME, hostname));
        return Result.success(hostListPageDTOS).put(Constants.TOTAL, count);
    }
    
    @Override
    public List<ClusterHostDO> getHostListByClusterId(Integer clusterId) {
        return this.list(new QueryWrapper<ClusterHostDO>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.MANAGED, 1));
    }
    
    @Override
    public Result getRoleListByHostname(Integer clusterId, String hostname) {
        List<ClusterServiceRoleInstanceEntity> list =
                roleInstanceMapper.getServiceRoleListByHostnameAndClusterId(hostname, clusterId);
        for (ClusterServiceRoleInstanceEntity roleInstanceEntity : list) {
            roleInstanceEntity.setServiceRoleStateCode(roleInstanceEntity.getServiceRoleState().getValue());
        }
        return Result.success(list);
    }
    
    /**
     * 批量删除主机。
     * 删除主机，首先停止主机上的服务
     * 其次删除主机 worker，同时移除 Prometheus hosts
     * 然后删除主机运行的实例
     *
     * @param hostIds
     * @return
     */
    @Override
    @Transactional
    public Result deleteHosts(String hostIds) {
        // 批量移除
        String[] ids = hostIds.split(Constants.COMMA);
        for (String hostId : ids) {
            ClusterHostDO host = this.getById(hostId);
            // 获取主机上安装的服务
            List<ClusterServiceRoleInstanceEntity> list =
                    roleInstanceMapper.selectList(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                            .eq(Constants.CLUSTER_ID, host.getClusterId())
                            .eq(Constants.HOSTNAME, host.getHostname())
                            .eq(Constants.SERVICE_ROLE_STATE, ServiceRoleState.RUNNING)
                            .ne(Constants.ROLE_TYPE, RoleType.CLIENT));
            List<String> roles = list.stream().map(ClusterServiceRoleInstanceEntity::getServiceRoleName)
                    .collect(Collectors.toList());
            if (!list.isEmpty()) {
                return Result.error(host.getHostname() + Status.HOST_EXIT_ONE_RUNNING_ROLE.getMsg() + roles);
            }
            ClusterInfoEntity clusterInfo = clusterInfoMapper.selectById(host.getClusterId());
            String clusterCode = clusterInfo.getClusterCode();
            String distributeAgentKey = clusterCode + Constants.UNDERLINE + Constants.START_DISTRIBUTE_AGENT;
            if (CacheUtils.containsKey(distributeAgentKey + Constants.UNDERLINE + host.getHostname())) {
                CacheUtils.removeKey(distributeAgentKey + Constants.UNDERLINE + host.getHostname());
            }
            
            this.removeById(hostId);
            
            if (host.getHostState() != HostState.OFFLINE) {
                // stop the worker on this host
                ExecuteCmdCommand command = new ExecuteCmdCommand();
                ArrayList<String> commands = new ArrayList<>();
                commands.add("service");
                commands.add("datasophon-worker");
                commands.add("stop");
                command.setCommands(commands);
                try {
                    workerCallAdapter.executeCmd(host.getHostname(), command);
                } catch (Exception e) {
                    logger.warn("Failed to stop worker on host {}: {}", host.getHostname(), e.getMessage());
                }
            }
            // Prometheus 移除 hosts 信息
            GenerateHostPrometheusConfig prometheusConfigCommand = new GenerateHostPrometheusConfig();
            prometheusConfigCommand.setClusterId(clusterInfo.getId());
            prometheusService.generateHostPrometheusConfig(prometheusConfigCommand);
            
            // remove the host from the cache
            Map<String, HostInfo> map =
                    (Map<String, HostInfo>) CacheUtils.get(clusterCode + Constants.HOST_MAP);
            String md5 = SecureUtil.md5(host.getHostname());
            if (Objects.nonNull(map)) {
                map.remove(host.getHostname());
            }
            if (CacheUtils.containsKey(clusterCode + Constants.HOST_MD5)
                    && md5.equals(CacheUtils.getString(clusterCode + Constants.HOST_MD5))) {
                CacheUtils.removeKey(clusterCode + Constants.HOST_MD5);
            }
        }
        return Result.success();
    }
    
    @Override
    public Result getRack(Integer clusterId) {
        ArrayList<JSONObject> list = new ArrayList<>();
        JSONObject rack = new JSONObject();
        rack.put("rack", "/default-rack");
        list.add(rack);
        return Result.success(list);
    }
    
    @Override
    public void removeHostByClusterId(Integer clusterId) {
        this.remove(new QueryWrapper<ClusterHostDO>().eq(Constants.CLUSTER_ID, clusterId));
    }
    
    @Override
    public void updateBatchNodeLabel(List<String> hostIds, String nodeLabel) {
        List<ClusterHostDO> list = this.lambdaQuery().in(ClusterHostDO::getId, hostIds).list();
        for (ClusterHostDO clusterHostDO : list) {
            clusterHostDO.setNodeLabel(nodeLabel);
        }
        this.updateBatchById(list);
    }
    
    @Override
    public List<ClusterHostDO> getHostListByIds(List<String> ids) {
        return this.lambdaQuery().in(ClusterHostDO::getId, ids).or().in(ClusterHostDO::getHostname, ids).list();
    }
    
    @Override
    public Result assignRack(Integer clusterId, String rack, String hostIds) {
        List<String> ids = Arrays.asList(hostIds.split(","));
        List<ClusterHostDO> list = this.lambdaQuery().in(ClusterHostDO::getId, ids).list();
        for (ClusterHostDO clusterHostDO : list) {
            clusterHostDO.setRack(rack);
        }
        this.updateBatchById(list);
        GenerateRackPropCommand command = new GenerateRackPropCommand();
        command.setClusterId(clusterId);
        rackService.generateRackProp(command);
        return Result.success();
    }
    
}
