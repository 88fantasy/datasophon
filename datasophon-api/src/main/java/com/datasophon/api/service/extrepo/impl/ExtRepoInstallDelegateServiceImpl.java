package com.datasophon.api.service.extrepo.impl;

import com.datasophon.api.dag.model.EdgeDefinition;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.api.service.extrepo.ExtRepoInstallDelegateService;
import com.datasophon.api.service.extrepo.ExtRepoInstallService;
import com.datasophon.api.service.extrepo.utils.MetaUtils;
import com.datasophon.api.service.extrepo.utils.ServiceNodeExecUtils;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.vo.extrepo.InstallProgressDAG;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.function.ThrowableMapper;
import com.datasophon.common.model.ServiceNode;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.model.k8s.K8sServiceNode;
import com.datasophon.common.utils.YamlUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostCommandEntity;
import com.datasophon.dao.entity.dag.DagDefinitionEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.mapper.dag.DagDefinitionEntityMapper;
import com.datasophon.dao.model.extrepo.DeploymentModel;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSONObject;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;

/**
 * @author zhanghuangbin
 */
@Service("extRepoInstallDelegateService")
@Transactional(rollbackFor = Exception.class)
public class ExtRepoInstallDelegateServiceImpl implements ExtRepoInstallDelegateService {
    
    private static final Logger log = LoggerFactory.getLogger(ExtRepoInstallDelegateServiceImpl.class);
    
    @Autowired
    private UploadTempFileService uploadTempFileService;
    
    @Autowired
    private ClusterInfoService clusterInfoService;
    
    @Autowired
    private ClusterServiceCommandHostCommandService hostCommandService;
    
    @Autowired
    private DAGService dagService;
    
    @Autowired
    private DagDefinitionEntityMapper dagDefinitionEntityMapper;
    
    @Autowired
    @Qualifier("physicalProductInstallService")
    private ExtRepoInstallService vosExtRepoInstallService;
    
    @Autowired
    @Qualifier("k8SProductInstallService")
    private ExtRepoInstallService k8SExtRepoInstallService;
    @Autowired
    private ClusterK8sServiceCommandService clusterK8sServiceCommandService;
    
    @Override
    public ValidateResultVO validDeploymentFile(DeploymentDTO dto) {
        DeploymentModel model = doParseDeploymentFile(dto);
        return doInTargetHandler(dto.getClusterId(), handler -> handler.validateDeploymentModel(model, dto));
    }
    
    private DeploymentModel doParseDeploymentFile(DeploymentDTO dto) {
        File deploymentFile = uploadTempFileService.getTempFile(dto.getDeployFileId())
                .orElseThrow(() -> new BusinessHintException("部署清单文件不存在"));
        String content = MetaUtils.decodeFile(deploymentFile, dto.getContentDecodePasswd());
        DeploymentModel model = YamlUtils.parseYaml(content, DeploymentModel.class);
        return model;
    }
    
    private <T> T doInTargetHandler(Integer clusterId, ThrowableMapper<ExtRepoInstallService, T> consumer) {
        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        try {
            if (cluster.getArchType().equals(ClusterArchType.physical)) {
                return consumer.accept(vosExtRepoInstallService);
            } else {
                return consumer.accept(k8SExtRepoInstallService);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
    
    @Override
    public InstallResult deploy(DeploymentDTO dto) {
        return doInTargetHandler(dto.getClusterId(), handler -> handler.deploy(dto));
    }
    
    @Override
    public void redeploy(RunDagDto dto) {
        DagDefinitionEntity def = dagDefinitionEntityMapper.selectById(dto.getDagId());
        if (!Arrays.asList(DagStatus.PENDING, DagStatus.FAILED, DagStatus.CANCEL).contains(def.getStatus())) {
            throw new BusinessHintException(String.format("当前任务的状态为%s，不允许重复运行", def.getStatus().name()));
        }
        if (def.getCreatedTime().plusDays(1).isBefore(LocalDateTime.now())) {
            throw new BusinessHintException("任务已经过期, 不允许在运行");
        }
        List<NodeDefinition> nodes = dagService.getNodesByDagId(dto.getDagId(), true);
        if (nodes.isEmpty()) {
            return;
        }
        
        doInTargetHandler(def.getClusterId(), handler -> {
            handler.redeploy(dto);
            return null;
        });
    }
    
    @Override
    public InstallProgressDAG getDeployProgressDAG2(String dagId) {
        DagDefinitionEntity def = dagDefinitionEntityMapper.selectById(dagId);
        InstallProgressDAG result = BeanUtil.toBean(def, InstallProgressDAG.class);
        
        List<NodeDefinition> nodes = dagService.getNodesByDagId(dagId, true);
        if (nodes.isEmpty()) {
            return result;
        }
        result.setClusterId(def.getClusterId());
        ClusterInfoEntity cluster = clusterInfoService.getById(def.getClusterId());
        result.setArchType(cluster.getArchType());
        
        List<InstallProgressDAG.Node> resultNodes = new ArrayList<>();
        if (ClusterArchType.physical.equals(cluster.getArchType())) {
            for (NodeDefinition node : nodes) {
                InstallProgressDAG.Node resultNode = BeanUtil.toBean(node, InstallProgressDAG.Node.class);
                ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);
                
                String cmdId = serviceNode.getCommandId();
                resultNode.setCommandId(cmdId);
                List<InstallProgressDAG.SrvRole> roles = createSrvRole(cmdId, serviceNode);
                resultNode.setRoles(roles);
                resultNodes.add(resultNode);
            }
        } else {
            List<String> commandIds = new ArrayList<>();
            for (NodeDefinition node : nodes) {
                InstallProgressDAG.Node resultNode = BeanUtil.toBean(node, InstallProgressDAG.Node.class);
                K8sServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), K8sServiceNode.class);
                
                String cmdId = serviceNode.getCommandId();
                commandIds.add(cmdId);
                resultNode.setCommandId(cmdId);
                resultNodes.add(resultNode);
            }
            List<ClusterK8sServiceCommandEntity> commands = clusterK8sServiceCommandService.listByIds(commandIds);
            Map<String, ClusterK8sServiceCommandEntity> map = CollectionUtil.toMap(commands, new HashMap<>(), ClusterK8sServiceCommandEntity::getCommandId);
            resultNodes.forEach(n -> {
                ClusterK8sServiceCommandEntity cmd = map.get(n.getCommandId());
                n.setK8s(cmd);
            });
            
        }
        
        result.setNodes(resultNodes);
        
        List<InstallProgressDAG.EdgeVO> edges = new ArrayList<>();
        for (EdgeDefinition edgeDef : dagService.getEdgesByDagId(dagId)) {
            InstallProgressDAG.EdgeVO edge = new InstallProgressDAG.EdgeVO();
            edge.setId(edgeDef.getId());
            edge.setStart(edgeDef.getFromNodeId());
            edge.setEnd(edgeDef.getToNodeId());
            edges.add(edge);
        }
        result.setEdges(edges);
        
        return result;
    }
    
    private List<InstallProgressDAG.SrvRole> createSrvRole(String cmdId, ServiceNode serviceNode) {
        List<ClusterServiceCommandHostCommandEntity> hostCmdList = hostCommandService.lambdaQuery()
                .eq(ClusterServiceCommandHostCommandEntity::getCommandId, cmdId)
                .list();
        Map<String, ClusterServiceCommandHostCommandEntity> map = CollectionUtil.toMap(hostCmdList, new HashMap<>(),
                ClusterServiceCommandHostCommandEntity::getHostCommandId);
        List<InstallProgressDAG.SrvRole> roles = new ArrayList<>();
        
        ServiceNodeExecUtils.getSortedRoleInfo(serviceNode.getCommandType(), serviceNode.getMasterRoles()).forEach(pair -> roles.add(newRole(pair, map)));
        ServiceNodeExecUtils.getSortedRoleInfo(serviceNode.getCommandType(), serviceNode.getClientRoles()).forEach(pair -> roles.add(newRole(pair, map)));
        ServiceNodeExecUtils.getSortedRoleInfo(serviceNode.getCommandType(), serviceNode.getWorkerRoles()).forEach(pair -> roles.add(newRole(pair, map)));
        return roles;
    }
    
    private InstallProgressDAG.SrvRole newRole(Pair<String, List<ServiceRoleInfo>> pair, Map<String, ClusterServiceCommandHostCommandEntity> map) {
        InstallProgressDAG.SrvRole role = new InstallProgressDAG.SrvRole();
        role.setRoleName(pair.getKey());
        role.setCmdList(new ArrayList<>());
        pair.getValue().forEach(serviceRole -> {
            ClusterServiceCommandHostCommandEntity hostCmd = map.get(serviceRole.getHostCommandId());
            if (hostCmd != null) {
                InstallProgressDAG.HostCmd cmd = BeanUtil.toBean(hostCmd, InstallProgressDAG.HostCmd.class, CopyOptions.create().setIgnoreProperties("commandState"));
                if (hostCmd.getCommandState() != null) {
                    cmd.setCommandState(hostCmd.getCommandState().name());
                    cmd.setCommandStateName(hostCmd.getCommandState().getDesc());
                }
                role.getCmdList().add(cmd);
            }
        });
        return role;
    }
    
    @Override
    public String generateGenericInstallCommand(Integer clusterId, List<String> serviceNames) {
        return doInTargetHandler(clusterId, handler -> handler.generateGenericInstallCommand(clusterId, serviceNames));
    }
    
    @Override
    public String generateAndExecSrvInstCmd(Integer clusterId, CommandType commandType, List<Integer> serviceInstanceIds) {
        if (Arrays.asList(CommandType.UPGRADE_SERVICE, CommandType.INSTALL_SERVICE).contains(commandType)) {
            throw new UnsupportedOperationException(String.format("command %s is not support", commandType));
        }
        return doInTargetHandler(clusterId, handler -> handler.generateAndExecSrvInstCmd(clusterId, commandType, serviceInstanceIds));
        
    }
    
}
