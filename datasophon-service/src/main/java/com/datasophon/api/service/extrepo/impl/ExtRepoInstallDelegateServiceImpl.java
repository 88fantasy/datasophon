package com.datasophon.api.service.extrepo.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.dag.model.EdgeDefinition;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.ClusterServiceCommandHostService;
import com.datasophon.api.service.ClusterServiceCommandService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.api.service.extrepo.ExtRepoInstallDelegateService;
import com.datasophon.api.service.extrepo.ExtRepoInstallService;
import com.datasophon.api.service.extrepo.utils.MetaUtils;
import com.datasophon.api.service.extrepo.utils.ServiceNodeExecUtils;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.utils.TransactionalUtils;
import com.datasophon.api.vo.extrepo.InstallProgressDAG2;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.function.ThrowableMapper;
import com.datasophon.common.model.ServiceNode;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.YamlUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceCommandHostCommandEntity;
import com.datasophon.dao.entity.dag.DagDefinitionEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.mapper.dag.DagDefinitionEntityMapper;
import com.datasophon.dao.model.extrepo.DeploymentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhanghuangbin
 * @date 2025/11/18
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
    private FrameServiceService frameService;

    @Autowired
    private ServiceInstallService serviceInstallService;


    @Autowired
    private ClusterServiceInstanceService clusterServiceInstanceService;

    @Autowired
    private FrameServiceRoleService frameServiceRoleService;

    @Autowired
    private ClusterServiceCommandHostService commandHostService;

    @Autowired
    private ClusterServiceCommandHostCommandService hostCommandService;


    @Autowired
    private ClusterServiceCommandService commandService;

    @Autowired
    private ClusterServiceInstanceService serviceInstanceService;

    @Autowired
    private ClusterServiceRoleInstanceService roleInstanceService;

    @Autowired
    private ClusterHostService clusterHostService;

    @Autowired
    private DAGService dagService;


    @Autowired
    private DagDefinitionEntityMapper dagDefinitionEntityMapper;

    @Autowired
    private TransactionalUtils transactionalUtils;

    @Autowired
    @Qualifier("vosProductInstallService")
    private ExtRepoInstallService vosExtRepoInstallService;


    @Autowired
    @Qualifier("k8SProductInstallService")
    private ExtRepoInstallService k8SExtRepoInstallService;

    @Override
    public ValidateResultVO validDeploymentFile(DeploymentDTO dto) {
        DeploymentModel model = doParseDeploymentFile(dto);
        return doInTargetHandler(dto.getClusterId(), handler -> handler.validateDeploymentModel(model, dto));
    }


    private DeploymentModel doParseDeploymentFile(DeploymentDTO dto) {
        File deploymentFile = uploadTempFileService.getTempFile(dto.getDeployFileId());
        if (deploymentFile == null) {
            throw new BusinessHintException("部署清单文件不存在");
        }
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
    public InstallProgressDAG2 getDeployProgressDAG2(String dagId) {
        DagDefinitionEntity def = dagDefinitionEntityMapper.selectById(dagId);
        InstallProgressDAG2 result = BeanUtil.toBean(def, InstallProgressDAG2.class);

        List<NodeDefinition> nodes = dagService.getNodesByDagId(dagId, true);
        if (nodes.isEmpty()) {
            return result;
        }
        result.setClusterId(def.getClusterId());
        List<InstallProgressDAG2.Node> resultNodes = new ArrayList<>();
        for (NodeDefinition node : nodes) {
            InstallProgressDAG2.Node resultNode = BeanUtil.toBean(node, InstallProgressDAG2.Node.class);
            ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);

            String cmdId = serviceNode.getCommandId();
            resultNode.setCommandId(cmdId);
            List<InstallProgressDAG2.SrvRole> roles = createSrvRole(cmdId, serviceNode);
            resultNode.setRoles(roles);
            resultNodes.add(resultNode);
        }
        result.setNodes(resultNodes);

        List<InstallProgressDAG2.EdgeVO> edges = new ArrayList<>();
        for (EdgeDefinition edgeDef : dagService.getEdgesByDagId(dagId)) {
            InstallProgressDAG2.EdgeVO edge = new InstallProgressDAG2.EdgeVO();
            edge.setId(edgeDef.getId());
            edge.setStart(edgeDef.getFromNodeId());
            edge.setEnd(edgeDef.getToNodeId());
            edges.add(edge);
        }
        result.setEdges(edges);

        return result;
    }


    private List<InstallProgressDAG2.SrvRole> createSrvRole(String cmdId, ServiceNode serviceNode) {
        List<ClusterServiceCommandHostCommandEntity> hostCmdList = hostCommandService.lambdaQuery()
                .eq(ClusterServiceCommandHostCommandEntity::getCommandId, cmdId)
                .list();
        Map<String, ClusterServiceCommandHostCommandEntity> map = CollectionUtil.toMap(hostCmdList, new HashMap<>(),
                ClusterServiceCommandHostCommandEntity::getHostCommandId);
        List<InstallProgressDAG2.SrvRole> roles = new ArrayList<>();


        ServiceNodeExecUtils.getSortedRoleInfo(serviceNode.getCommandType(), serviceNode.getMasterRoles()).forEach(pair -> roles.add(newRole(pair, map)));
        ServiceNodeExecUtils.getSortedRoleInfo(serviceNode.getCommandType(), serviceNode.getClientRoles()).forEach(pair -> roles.add(newRole(pair, map)));
        ServiceNodeExecUtils.getSortedRoleInfo(serviceNode.getCommandType(), serviceNode.getWorkerRoles()).forEach(pair -> roles.add(newRole(pair, map)));
        return roles;
    }

    private InstallProgressDAG2.SrvRole newRole(Pair<String, List<ServiceRoleInfo>> pair, Map<String, ClusterServiceCommandHostCommandEntity> map) {
        InstallProgressDAG2.SrvRole role = new InstallProgressDAG2.SrvRole();
        role.setRoleName(pair.getKey());
        role.setCmdList(new ArrayList<>());
        pair.getValue().forEach(serviceRole -> {
            ClusterServiceCommandHostCommandEntity hostCmd = map.get(serviceRole.getHostCommandId());
            if (hostCmd != null) {
                InstallProgressDAG2.HostCmd cmd = BeanUtil.toBean(hostCmd, InstallProgressDAG2.HostCmd.class, CopyOptions.create().setIgnoreProperties("commandState"));
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
        return doInTargetHandler(clusterId, handler-> handler.generateGenericInstallCommand(clusterId, serviceNames));
    }


    @Override
    public String generateAndExecSrvInstCmd(Integer clusterId, CommandType commandType, List<Integer> serviceInstanceIds) {
        if (Arrays.asList(CommandType.UPGRADE_SERVICE, CommandType.INSTALL_SERVICE).contains(commandType)) {
            throw new UnsupportedOperationException(String.format("command %s is not support", commandType));
        }
        return doInTargetHandler(clusterId, handler-> handler.generateAndExecSrvInstCmd(clusterId, commandType, serviceInstanceIds));


    }







}
