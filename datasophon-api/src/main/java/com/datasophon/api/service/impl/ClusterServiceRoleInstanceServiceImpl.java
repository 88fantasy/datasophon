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
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceWebuisService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.extrepo.ExtRepoInstallDelegateService;
import com.datasophon.api.service.extrepo.PhysicalProductInstallService;
import com.datasophon.api.utils.WorkerFanOutUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.command.GetLogCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.CollectionUtils;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.RoleType;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.dao.mapper.ClusterServiceRoleInstanceMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.hutool.core.util.StrUtil;

@Service("clusterServiceRoleInstanceService")
@RequiredArgsConstructor
public class ClusterServiceRoleInstanceServiceImpl
        extends
            ServiceImpl<ClusterServiceRoleInstanceMapper, ClusterServiceRoleInstanceEntity>
        implements
            ClusterServiceRoleInstanceService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterServiceRoleInstanceServiceImpl.class);
    
    // 注：本类处于多条既有循环依赖链上（rackService→本类→frameService→ddlMetaService→本类 等），
    // 构造器注入下环上的依赖须用 @Lazy 延迟解析打破环。
    @Lazy
    private final ClusterInfoService clusterInfoService;
    
    @Lazy
    private final FrameServiceRoleService frameServiceRoleService;
    
    @Lazy
    private final FrameServiceService frameService;
    
    @Lazy
    private final ExtRepoInstallDelegateService extRepoInstallDelegateService;
    
    @Lazy
    private final PhysicalProductInstallService vosProductActionService;
    
    @Lazy
    private final ClusterServiceInstanceRoleGroupService roleGroupService;
    
    private final ClusterServiceRoleInstanceMapper roleInstanceMapper;
    
    private final WorkerCommandClient workerCommandClient;
    
    @Lazy
    private final ClusterAlertHistoryService alertHistoryService;
    
    @Lazy
    private final ClusterServiceRoleInstanceWebuisService webuisService;
    
    @Override
    public List<ClusterServiceRoleInstanceEntity> listStoppedServiceRoleListByHostnameAndClusterId(String hostname, Integer clusterId) {
        return roleInstanceMapper.listStoppedServiceRoleListByHostnameAndClusterId(hostname, clusterId);
    }
    
    @Override
    public List<ClusterServiceRoleInstanceEntity> getServiceRoleListByHostnameAndClusterId(String hostname, Integer clusterId) {
        return roleInstanceMapper.getServiceRoleListByHostnameAndClusterId(hostname, clusterId);
    }
    
    @Override
    public ClusterServiceRoleInstanceEntity getOneServiceRole(String serviceRoleName, String hostname, Integer clusterId) {
        return roleInstanceMapper.getOneServiceRole(serviceRoleName, hostname, clusterId);
    }
    
    @Override
    public Result listAll(Integer serviceInstanceId, String hostname, Integer serviceRoleState, String serviceRoleName,
                          Integer roleGroupId, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        
        LambdaQueryChainWrapper<ClusterServiceRoleInstanceEntity> wrapper = this.lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceId, serviceInstanceId)
                .eq(Objects.nonNull(serviceRoleState), ClusterServiceRoleInstanceEntity::getServiceRoleState,
                        serviceRoleState)
                .eq(StringUtils.isNotBlank(serviceRoleName), ClusterServiceRoleInstanceEntity::getServiceRoleName,
                        serviceRoleName)
                .eq(Objects.nonNull(roleGroupId), ClusterServiceRoleInstanceEntity::getRoleGroupId, roleGroupId)
                .like(StringUtils.isNotBlank(hostname), ClusterServiceRoleInstanceEntity::getHostname, hostname);
        long count = wrapper.count() == null ? 0 : wrapper.count();
        List<ClusterServiceRoleInstanceEntity> cluServiceRoleInstList = wrapper
                .last("limit " + offset + "," + pageSize)
                .list();
        if (CollectionUtils.isEmpty(cluServiceRoleInstList)) {
            return Result.successEmptyCount();
        }
        
        // 去重后一次查回角色组，避免分页内逐行 getById 的 N+1 查询
        List<Integer> roleGroupIds = cluServiceRoleInstList.stream()
                .map(ClusterServiceRoleInstanceEntity::getRoleGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, ClusterServiceInstanceRoleGroup> roleGroupMap = roleGroupIds.isEmpty()
                ? Collections.emptyMap()
                : roleGroupService.listByIds(roleGroupIds).stream()
                        .collect(Collectors.toMap(ClusterServiceInstanceRoleGroup::getId, rg -> rg));
        for (ClusterServiceRoleInstanceEntity roleInstanceEntity : cluServiceRoleInstList) {
            ClusterServiceInstanceRoleGroup roleGroup = roleGroupMap.get(roleInstanceEntity.getRoleGroupId());
            if (Objects.nonNull(roleGroup)) {
                roleInstanceEntity.setRoleGroupName(roleGroup.getRoleGroupName());
            }
            roleInstanceEntity.setServiceRoleStateCode(roleInstanceEntity.getServiceRoleState().getValue());
        }
        
        return Result.success(cluServiceRoleInstList).put(Constants.TOTAL, count);
    }
    
    @Override
    public Result getLog(Integer serviceRoleInstanceId) throws Exception {
        ClusterServiceRoleInstanceEntity roleInstance = this.getById(serviceRoleInstanceId);
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(roleInstance.getClusterId());
        FrameServiceRoleEntity serviceRole = frameServiceRoleService.getServiceRoleByFrameCodeAndServiceRoleName(clusterInfo.getClusterFrame(), roleInstance.getServiceRoleName());
        
        Map<String, String> globalVariables = GlobalVariables.getVariables(roleInstance.getClusterId());
        if (serviceRole.getServiceRoleType() == RoleType.CLIENT) {
            return Result.success("client service role type does not have any log");
        }
        String logFile = serviceRole.getLogFile();
        if (StringUtils.isNotBlank(logFile)) {
            logFile = PlaceholderUtils.replacePlaceholders(logFile, globalVariables, Constants.REGEX_VARIABLE);
        }
        
        ServiceRoleInfo serviceRoleInfo = JSONObject.parseObject(serviceRole.getServiceRoleJson(), ServiceRoleInfo.class);
        String user = serviceRoleInfo.getRunAs() != null ? serviceRoleInfo.getRunAs().getUser() : null;
        if (StrUtil.isBlank(user)) {
            user = "root";
        }
        Map<String, String> params = Collections.singletonMap("user", user);
        if (StringUtils.isNotBlank(logFile)) {
            logFile = PlaceholderUtils.replacePlaceholders(logFile, params, Constants.REGEX_VARIABLE);
            logger.info("logFile is {}", logFile);
        }
        
        GetLogCommand command = new GetLogCommand();
        command.setLogFile(logFile);
        command.setBaseDir(PkgInstallPathUtils.getInstallUniHome(serviceRoleInfo));
        
        logger.info("start to get {} log from {}", serviceRole.getServiceRoleName(), roleInstance.getHostname());
        ExecResult logResult = workerCommandClient.getLog(roleInstance.getHostname(), command.getLogFile(), command.getBaseDir());
        if (Objects.nonNull(logResult) && logResult.getExecResult()) {
            return Result.success(logResult.getExecOut());
        }
        return Result.success();
    }
    
    @Override
    public List<ClusterServiceRoleInstanceEntity> getServiceRoleInstanceListByServiceId(int id) {
        return roleInstanceMapper.getServiceRoleInstanceListByServiceId(id);
    }
    
    @Override
    public List<ClusterServiceRoleInstanceEntity> getServiceRoleInstanceListByClusterId(int clusterId) {
        return roleInstanceMapper.getServiceRoleInstanceListByClusterId(clusterId);
    }
    
    @Override
    public Result deleteServiceRole(List<String> idList) {
        Collection<ClusterServiceRoleInstanceEntity> list = this.listByIds(idList);
        // is there a running instance
        boolean flag = false;
        ArrayList<Integer> needRemoveList = new ArrayList<>();
        for (ClusterServiceRoleInstanceEntity instance : list) {
            if (instance.getServiceRoleState() == ServiceRoleState.RUNNING) {
                flag = true;
            } else {
                needRemoveList.add(instance.getId());
            }
        }
        if (!needRemoveList.isEmpty()) {
            alertHistoryService.removeAlertByRoleInstanceIds(needRemoveList);
            this.removeByIds(needRemoveList);
            // delete if there is a webui
            webuisService.removeByRoleInsIds(needRemoveList);
        }
        return flag ? Result.error(Status.EXIT_RUNNING_INSTANCES.getMsg()) : Result.success();
    }
    
    @Override
    public List<ClusterServiceRoleInstanceEntity> getServiceRoleInstanceListByClusterIdAndRoleName(Integer clusterId,
                                                                                                   String roleName) {
        return this.list(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                .eq(Constants.CLUSTER_ID, clusterId).eq(Constants.SERVICE_ROLE_NAME, roleName));
    }
    
    @Override
    public Result restartObsoleteService(Integer roleGroupId) {
        ClusterServiceInstanceRoleGroup roleGroup = roleGroupService.getById(roleGroupId);
        List<ClusterServiceRoleInstanceEntity> list = this.list(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                .eq(Constants.ROLE_GROUP_ID, roleGroupId)
                .eq(Constants.NEET_RESTART, NeedRestart.YES));
        if (Objects.nonNull(list) && !list.isEmpty()) {
            List<Integer> ids = list.stream().map(ClusterServiceRoleInstanceEntity::getId).collect(Collectors.toList());
            vosProductActionService.generateAndExecSrvRoleCmd(roleGroup.getClusterId(), CommandType.RESTART_SERVICE, roleGroup.getServiceInstanceId(), ids);
        } else {
            return Result.error(Status.ROLE_GROUP_HAS_NO_OUTDATED_SERVICE.getMsg());
        }
        return Result.success();
    }
    
    @Override
    public Result decommissionNode(String serviceRoleInstanceIds, String serviceName) throws Exception {
        TreeSet<String> hosts = new TreeSet<>();
        Integer serviceInstanceId = null;
        String serviceRoleName = "";
        for (String str : serviceRoleInstanceIds.split(",")) {
            int serviceRoleInstanceId = Integer.parseInt(str);
            ClusterServiceRoleInstanceEntity roleInstanceEntity = this.getById(serviceRoleInstanceId);
            if ("DataNode".equals(roleInstanceEntity.getServiceRoleName())
                    || "NodeManager".equals(roleInstanceEntity.getServiceRoleName())) {
                hosts.add(roleInstanceEntity.getHostname());
                serviceInstanceId = roleInstanceEntity.getServiceId();
                serviceRoleName = roleInstanceEntity.getServiceRoleName();
                roleInstanceEntity.setServiceRoleState(ServiceRoleState.DECOMMISSIONING);
                this.updateById(roleInstanceEntity);
            }
        }
        // 查询已退役节点
        List<ClusterServiceRoleInstanceEntity> list = this.lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceRoleState, ServiceRoleState.DECOMMISSIONING)
                .in(ClusterServiceRoleInstanceEntity::getId, serviceRoleInstanceIds)
                .list();
        // 添加已退役节点到黑名单
        for (ClusterServiceRoleInstanceEntity roleInstanceEntity : list) {
            hosts.add(roleInstanceEntity.getHostname());
        }
        String type = "blacklist";
        String roleName = "NameNode";
        if ("nodemanager".equalsIgnoreCase(serviceRoleName)) {
            type = "nmexclude";
            roleName = "ResourceManager";
        }
        if (!hosts.isEmpty()) {
            WorkerFanOutUtils.hdfsEcMethond(serviceInstanceId, this, hosts, "blacklist", roleName);
        }
        return Result.success();
    }
    
    @Override
    public void updateToNeedRestart(Integer roleGroupId) {
        roleInstanceMapper.updateToNeedRestart(roleGroupId);
    }
    
    @Override
    public void updateToNeedRestartByHost(String hostName) {
        roleInstanceMapper.updateToNeedRestartByHost(hostName);
    }
    
    @Override
    public List<ClusterServiceRoleInstanceEntity> listRoleIns(String hostname, String serviceName) {
        return roleInstanceMapper.listRoleIns(hostname, serviceName);
    }
    
}
