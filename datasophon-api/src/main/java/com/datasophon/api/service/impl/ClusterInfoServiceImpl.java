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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.enums.Status;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.service.ClusterDeleteService;
import com.datasophon.api.service.AlertGroupService;
import com.datasophon.api.service.ClusterAlertGroupMapService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterNodeLabelService;
import com.datasophon.api.service.ClusterQueueCapacityService;
import com.datasophon.api.service.ClusterRackService;
import com.datasophon.api.service.ClusterRoleUserService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterYarnSchedulerService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.utils.PackageUtils;
import com.datasophon.api.utils.SecurityUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.dao.entity.AlertGroupEntity;
import com.datasophon.dao.entity.ClusterAlertGroupMap;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.UserInfoEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ClusterState;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service("clusterInfoService")
@Transactional
public class ClusterInfoServiceImpl extends ServiceImpl<ClusterInfoMapper, ClusterInfoEntity>
        implements
        ClusterInfoService {

    @Autowired
    private ClusterInfoMapper clusterInfoMapper;

    @Autowired
    private ClusterRoleUserService clusterUserService;

    @Autowired
    private AlertGroupService alertGroupService;

    @Autowired
    private ClusterAlertGroupMapService groupMapService;


    @Autowired
    private ClusterYarnSchedulerService yarnSchedulerService;

    @Autowired
    private ClusterNodeLabelService nodeLabelService;

    @Autowired
    private ClusterQueueCapacityService queueCapacityService;

    @Autowired
    private ClusterRackService rackService;

    @Autowired
    private ClusterServiceInstanceService clusterServiceInstanceService;

    @Autowired
    private K8sServiceInstanceService k8sServiceInstanceService;

    @Autowired
    private ClusterDeleteService clusterDeleteService;

    @Override
    public ClusterInfoEntity getClusterByClusterCode(String clusterCode) {
        return clusterInfoMapper.getClusterByClusterCode(clusterCode);
    }

    @Override
    public ClusterInfoEntity saveCluster(ClusterInfoEntity clusterInfo) {
        if (getBaseMapper().isDuplicate(clusterInfo, ClusterInfoEntity::getClusterCode)) {
            throw new BusinessHintException(Status.CLUSTER_CODE_EXISTS.getMsg());
        }
        clusterInfo.setCreateTime(new Date());
        clusterInfo.setCreateBy(SecurityUtils.getAuthUser().getUsername());
        clusterInfo.setClusterState(ClusterState.NEED_CONFIG);
        save(clusterInfo);

        if (ClusterArchType.physical.equals(clusterInfo.getArchType())) {
            List<AlertGroupEntity> alertGroupList = alertGroupService.list();
            for (AlertGroupEntity alertGroupEntity : alertGroupList) {
                ClusterAlertGroupMap alertGroupMap = new ClusterAlertGroupMap();
                alertGroupMap.setAlertGroupId(alertGroupEntity.getId());
                alertGroupMap.setClusterId(clusterInfo.getId());
                groupMapService.save(alertGroupMap);
            }

            yarnSchedulerService.createDefaultYarnScheduler(clusterInfo.getId());

            nodeLabelService.createDefaultNodeLabel(clusterInfo.getId());

            queueCapacityService.createDefaultQueue(clusterInfo.getId());

            rackService.createDefaultRack(clusterInfo.getId());

            putClusterVariable(clusterInfo);
        }

        return clusterInfo;

    }

    private void putClusterVariable(ClusterInfoEntity clusterInfo) {
        ConcurrentHashMap<String, String> globalVariables = GlobalVariables.genDefaultGlobalVariables();
        globalVariables.put(GlobalVariables.surroundKey("HADOOP_HOME"),
                Constants.INSTALL_PATH + Constants.SLASH + PackageUtils.getServiceDcPackageName(clusterInfo.getClusterFrame(), "HDFS")
        );
        globalVariables.put(GlobalVariables.surroundKey(GlobalVariables.CLUSTER_CODE), clusterInfo.getClusterFrame());
        GlobalVariables.put(clusterInfo.getId(), globalVariables);
    }

    @Override
    public List<ClusterInfoEntity> getClusterList() {
        List<ClusterInfoEntity> list = this.list();
        for (ClusterInfoEntity clusterInfoEntity : list) {
            List<UserInfoEntity> userList = clusterUserService.getAllClusterManagerByClusterId(clusterInfoEntity.getId());
            clusterInfoEntity.setClusterManagerList(userList);
            clusterInfoEntity.setClusterStateCode(clusterInfoEntity.getClusterState().getValue());
        }
        return list;
    }

    @Override
    public List<ClusterInfoEntity> runningClusterList() {
        return lambdaQuery().eq(ClusterInfoEntity::getClusterState, ClusterState.RUNNING).list();
    }

    @Override
    public List<ClusterInfoEntity> getReadyClusterList() {
        return lambdaQuery()
                .notIn(ClusterInfoEntity::getClusterState, Arrays.asList(ClusterState.NEED_CONFIG, ClusterState.DELETING))
                .list();
    }

    @Override
    public void updateClusterState(Integer clusterId, Integer clusterState) {
        ClusterInfoEntity clusterInfo = this.getById(clusterId);
        ClusterState state = ClusterState.of(clusterState);
        if (state != null) {
            clusterInfo.setClusterState(state);
            this.updateById(clusterInfo);
        } else {
            throw new BusinessHintException("未知状态");
        }
    }

    @Override
    public List<ClusterInfoEntity> getClusterByFrameCode(String frameCode) {
        return this.list(new QueryWrapper<ClusterInfoEntity>().eq(Constants.CLUSTER_FRAME, frameCode));
    }

    @Override
    public void updateCluster(ClusterInfoEntity clusterInfo) {
        ClusterInfoEntity db = getById(clusterInfo.getId());
        if (db == null) {
            throw new BusinessHintException("对象空指针");
        }
        if (getBaseMapper().isDuplicate(clusterInfo, ClusterInfoEntity::getClusterCode)) {
            throw new BusinessHintException(Status.CLUSTER_CODE_EXISTS.getMsg());
        }
        if (db.getClusterCode().equals(clusterInfo.getClusterCode())) {
            CacheUtils.removeKey(db.getClusterCode() + Constants.HOST_MAP);
        }
        clusterInfo.setClusterStateCode(db.getClusterState().getValue());
        db.setClusterCode(clusterInfo.getClusterCode());
        db.setClusterName(clusterInfo.getClusterName());
        updateById(db);
    }

    @Override
    public void deleteCluster(Integer clusterId) {
        ClusterInfoEntity clusterInfo = this.getById(clusterId);
        if (clusterInfo == null) {
            return;
        }
        if (ClusterState.DELETING.equals(clusterInfo.getClusterState())) {
            throw new BusinessHintException("集群已经在删除中，不能重复删除");
        }

        if (ClusterArchType.physical.equals(clusterInfo.getArchType())) {
            boolean canDelete = false;
            if (ClusterState.STOP.equals(clusterInfo.getClusterState())) {
                List<ClusterServiceInstanceEntity> serviceInstanceList = clusterServiceInstanceService.listAll(clusterId);
                canDelete = serviceInstanceList.stream().noneMatch(instance -> clusterServiceInstanceService.hasRunningRoleInstance(instance.getId()));
            }
            if (!canDelete) {
                throw new BusinessHintException(String.format("集群%s存在正在运行的实例，不能删除。请先停止所有的实例", clusterInfo.getClusterName()));
            }
        } else {
            if (!ClusterState.NEED_CONFIG.equals(clusterInfo.getClusterState())) {
                boolean canDelete = !k8sServiceInstanceService.hasRunningInstance(clusterId);
                if (!canDelete) {
                    throw new BusinessHintException(String.format("集群%s存在正在运行的实例，不能删除。请先停止所有的实例", clusterInfo.getClusterName()));
                }
            }
        }
        updateClusterState(clusterId, ClusterState.DELETING.getValue());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                clusterDeleteService.deleteCluster(clusterId);
            }
        });
    }

}
