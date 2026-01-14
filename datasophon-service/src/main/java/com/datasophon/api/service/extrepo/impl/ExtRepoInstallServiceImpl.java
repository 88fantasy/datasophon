package com.datasophon.api.service.extrepo.impl;

import akka.actor.ActorRef;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.dag.model.DagDefinition;
import com.datasophon.api.dag.model.EdgeDefinition;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.load.LoadServiceMeta;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.master.DAGExecActor;
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
import com.datasophon.api.service.extrepo.ExtRepoInstallService;
import com.datasophon.api.service.extrepo.ctx.DeploymentDAGBuildContext;
import com.datasophon.api.service.extrepo.ctx.ExecDAGBuilderContext;
import com.datasophon.api.service.extrepo.utils.MetaUtils;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.strategy.ServiceRoleStrategyContext;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.TransactionalUtils;
import com.datasophon.api.vo.extrepo.DeploymentDAG;
import com.datasophon.api.vo.extrepo.InstallProgressDAG2;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.dag.DAGExecCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.DAG;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.common.model.ServiceNode;
import com.datasophon.common.model.ServiceRoleHostMapping;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.ClusterServiceCommandHostCommandEntity;
import com.datasophon.dao.entity.ClusterServiceCommandHostEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.entity.dag.NodeDefinitionEntity;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.enums.ServiceState;
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.enums.dag.NodeStatus;
import com.datasophon.dao.model.extrepo.DeploySrvConfig;
import com.datasophon.dao.model.extrepo.DeploymentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 * @date 2025/11/18
 */
@Service("extRepoInstallService")
public class ExtRepoInstallServiceImpl implements ExtRepoInstallService {

    private static final Logger log = LoggerFactory.getLogger(ExtRepoInstallServiceImpl.class);

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
    private ClusterServiceRoleInstanceService roleInstanceService;

    @Autowired
    private ClusterHostService clusterHostService;

    @Autowired
    private DAGService dagService;
    @Autowired
    private TransactionalUtils transactionalUtils;

    @Override
    public ValidateResultVO validDeploymentFile(DeploymentDTO dto) {
        File deploymentFile = uploadTempFileService.getTempFile(dto.getDeployFileId());
        if (deploymentFile == null) {
            throw new BusinessException("部署清单文件不存在");
        }
        String content = MetaUtils.decodeFile(deploymentFile, dto.getContentDecodePasswd());
        DeploymentModel model = MetaUtils.parseDeploymentFile(content);
        log.debug("解析到配置\n：{}", JSONObject.toJSONString(model, true));
        log.info("完成解析部署文件, 需要部署{}个应用", model.getApp().size());

        List<String> errors = new ArrayList<>();
        Set<String> deployHosts = model.getApp()
                .stream()
                .flatMap(app -> app.getRoles().stream())
                .flatMap(role -> role.getDeployHosts().stream())
                .collect(Collectors.toSet());
        List<ClusterHostDO> hostList = clusterHostService.getHostListByClusterId(dto.getClusterId());
        Map<String, ClusterHostDO> hostMap = hostList.stream().collect(Collectors.toMap(ClusterHostDO::getHostname, a -> a, (a, b) -> a));
        deployHosts = deployHosts.stream().filter(host -> !hostMap.containsKey(host)).collect(Collectors.toSet());
        if (!deployHosts.isEmpty()) {
            errors.add(String.format("以下主机%s不存在或者无法通讯", StrUtil.join(",", deployHosts)));
        }

        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());
        DeploymentDAGBuildContext ctx = new DeploymentDAGBuildContext(clusterInfo, serviceList);
        model.getApp().forEach(app -> {
            FrameServiceEntity entity = ctx.getSrvEntity(app);
            if (entity == null) {
                errors.add(String.format("服务%s %s不存在", app.getName(), app.getVersion()));
                return;
            }
            ClusterServiceInstanceEntity serviceInstance = clusterServiceInstanceService.getServiceInstanceByClusterIdAndServiceName(clusterInfo.getId(), entity.getServiceName());
            boolean exist = serviceInstance != null && commandService.lambdaQuery()
                    .eq(ClusterServiceCommandEntity::getServiceInstanceId, serviceInstance.getId())
                    .in(ClusterServiceCommandEntity::getCommandState, Arrays.asList(CommandState.RUNNING, CommandState.WAIT))
                    .exists();
            if (exist) {
                errors.add(String.format("服务%s %s正在执行命令，请等待命令执行完成或者取消命令", app.getName(), app.getVersion()));
            }
        });

        if (errors.isEmpty()) {
            ValidateResultVO vo = new ValidateResultVO();
            List<ValidateResultVO.DeploySrvRoleModel> roles = new ArrayList<>();
            model.getApp().forEach(app-> {
                app.getRoles().forEach(role-> {
                    ValidateResultVO.DeploySrvRoleModel tmp = new ValidateResultVO.DeploySrvRoleModel();
                    tmp.setServiceName(app.getName());
                    tmp.setVersion(app.getVersion());
                    tmp.setRoleName(role.getName());
                    tmp.setDeployHosts(role.getDeployHosts());
                    roles.add(tmp);
                });
            });
            vo.setRoles(roles);
            return vo;
        } else {
            return new ValidateResultVO(errors);
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstallResult deploy(DeploymentDTO dto) {
        ValidateResultVO result = validDeploymentFile(dto);
        if (!result.isSuccess()) {
            throw new BusinessException(result.getErrors());
        }

        File deploymentFile = uploadTempFileService.getTempFile(dto.getDeployFileId());
        String content = MetaUtils.decodeFile(deploymentFile, dto.getContentDecodePasswd());
        DeploymentModel model = MetaUtils.parseDeploymentFile(content);

//        保存serviceRole和host的映射
        List<ServiceRoleHostMapping> hostMappings = new ArrayList<>();
        model.getApp().stream()
                .flatMap(app -> app.getRoles().stream())
                .forEach(role -> {
                    ServiceRoleHostMapping hostMapping = new ServiceRoleHostMapping();
                    hostMapping.setHosts(role.getDeployHosts());
                    hostMapping.setServiceRole(role.getName());
                    hostMappings.add(hostMapping);
                });
        serviceInstallService.saveServiceRoleHostMapping(dto.getClusterId(), hostMappings);
        log.info("缓存存角色和host映射成功, 总共{}个映射", hostMappings.size());


//        保存应用的启动配置
        model.getApp().forEach(app -> {
            List<ServiceConfig> configs = serviceInstallService.getServiceConfigFromDdl(dto.getClusterId(), app.getName());
            Map<String, DeploySrvConfig> configMap = CollectionUtil.toMap(app.getConfig(), new HashMap<>(), DeploySrvConfig::getName);
            configs.forEach(conf -> {
                DeploySrvConfig deployConf = configMap.get(conf.getName());
                if (deployConf == null) {
                    conf.setValue(conf.getDefaultValue());
                } else {
                    conf.setValue(deployConf.getValue());
                }
            });
            serviceInstallService.saveServiceConfig(dto.getClusterId(), app.getName(), configs, -1);
        });
        log.info("保存部署配置项成功");


        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());
        DeploymentDAGBuildContext ctx = new DeploymentDAGBuildContext(clusterInfo, serviceList);
        Map<String, FrameServiceEntity> srvDefMap = CollectionUtil.toMap(serviceList, new HashMap<>(), srv -> srv.getServiceName() + ":" + srv.getServiceVersion());
        List<String> commandIds = new ArrayList<>(model.getApp().size());
        model.getApp().forEach(srv -> {
            String cmdId = generateCommand(clusterInfo, hostMappings, srvDefMap.get(srv.getName() + ":" + srv.getVersion()));
            commandIds.add(cmdId);
        });
        log.info("保存安装命令成功, 共需要安装{}个应用", commandIds.size());


        DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag = ctx.buildDeployDAG(model.getApp(), false);
        String dagId = saveDAG(clusterInfo.getId(), commandIds, dag);
//        必须等待事务提交，否则，有概率查询不到保存的命令
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doInstall(dagId, false, commandIds);
            }
        });
        return new InstallResult(dagId, commandIds);
    }


    public String generateCommand(ClusterInfoEntity cluster, List<ServiceRoleHostMapping> hostMappings, FrameServiceEntity frameService) {
        ClusterServiceInstanceEntity serviceInstance = clusterServiceInstanceService.getServiceInstanceByClusterIdAndServiceName(cluster.getId(), frameService.getServiceName());
        CommandType commandType = ServiceState.WAIT_INSTALL.equals(serviceInstance.getServiceState()) ? CommandType.INSTALL_SERVICE : CommandType.UPGRADE_SERVICE;
        ClusterServiceCommandEntity cmd = ProcessUtils.generateCommandEntity(cluster.getId(), commandType, frameService.getServiceName());
        cmd.setServiceInstanceId(serviceInstance.getId());
        commandService.save(cmd);
        log.info("保存{}{}命令成功, 命令ID:{}", commandType.getCommandName(Constants.CN), frameService.getServiceName(), cmd.getCommandId());

        Map<String, List<String>> serviceRoleHostMap = hostMappings.stream().collect(
                Collectors.toMap(
                        ServiceRoleHostMapping::getServiceRole,
                        a -> CollectionUtil.newArrayList(a.getHosts()),
                        (a, b) -> a
                )
        );

//        保存commandHost的相关数据
        List<ClusterServiceCommandHostEntity> hostEntityList = new ArrayList<>();
        List<FrameServiceRoleEntity> serviceRoleList = frameServiceRoleService.getServiceRoleList(cluster.getId(), Collections.singletonList(frameService.getId()), null);
        Set<String> hostnames = new HashSet<>();
        for (FrameServiceRoleEntity serviceRole : serviceRoleList) {
            hostnames.addAll(serviceRoleHostMap.getOrDefault(serviceRole.getServiceRoleName(), new ArrayList<>(0)));
        }
        hostnames.forEach(host -> hostEntityList.add(ProcessUtils.generateCommandHostEntity(cmd.getCommandId(), host)));
        commandHostService.saveBatch(hostEntityList);
        log.info("命令:{}{}保存各主机总命令信息成功,共涉及{}台主机",
                CommandType.ofCode(cmd.getCommandType()).getCommandName(Constants.CN),
                cmd.getServiceName(), hostEntityList.size());


//        保存每一台主机每一个角色需要执行的命令
        Map<String, ClusterServiceCommandHostEntity> cache = CollectionUtil.toMap(hostEntityList, new HashMap<>(), ClusterServiceCommandHostEntity::getHostname);
        List<ClusterServiceCommandHostCommandEntity> hostCommandList = new ArrayList<>();
        for (FrameServiceRoleEntity serviceRole : serviceRoleList) {
            List<String> hosts = serviceRoleHostMap.get(serviceRole.getServiceRoleName());
            if (hosts == null) {
                continue;
            }
            for (String hostname : hosts) {
                ClusterServiceRoleInstanceEntity db = roleInstanceService.getOneServiceRole(serviceRole.getServiceRoleName(), hostname, cluster.getId());

                CommandType roleCmdType = db == null ? CommandType.INSTALL_SERVICE : CommandType.UPGRADE_SERVICE;
                ClusterServiceCommandHostCommandEntity hostCommand = ProcessUtils.generateCommandHostCommandEntity(
                        roleCmdType, cmd.getCommandId(),
                        serviceRole.getServiceRoleName(), serviceRole.getServiceRoleType(),
                        cache.get(hostname)
                );
                hostCommandList.add(hostCommand);
            }
        }
        hostCommandService.saveBatch(hostCommandList);
        log.info("命令:{}{}保存各主机需要执行命令成功,共需要执行{}个命令", CommandType.ofCode(cmd.getCommandType()).getCommandName(Constants.CN),
                cmd.getServiceName(), hostCommandList.size());

        return cmd.getCommandId();
    }


    private String saveDAG(Integer clusterId, List<String> commandIds, DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);


        ExecDAGBuilderContext context = new ExecDAGBuilderContext();
        context.setSrvCmd(commandService.lambdaQuery().in(ClusterServiceCommandEntity::getCommandId, commandIds).list());
        context.setCmdHost(hostCommandService.lambdaQuery().in(ClusterServiceCommandHostCommandEntity::getCommandId, commandIds).list());
//        将服务依赖的dag重构成节点安装服务的dag
        Map<String, NodeDefinitionEntity> nodeMap = new HashMap<>();
        dag.getNodes().forEach((srv, info) -> {
            ServiceNode serviceNode = new ServiceNode();
            ClusterServiceCommandEntity cmd = context.getCmd(srv);
            serviceNode.setCommandId(cmd.getCommandId());
            serviceNode.setCommandType(CommandType.ofCode(cmd.getCommandType()));
            serviceNode.setServiceName(srv);


            FrameServiceEntity serviceEntity = frameService.lambdaQuery()
                    .eq(FrameServiceEntity::getFrameCode, clusterInfo.getClusterFrame())
                    .eq(FrameServiceEntity::getServiceName, info.getName())
                    .eq(FrameServiceEntity::getServiceVersion, info.getVersion())
                    .one();
            List<FrameServiceRoleEntity> srvRoles = frameServiceRoleService.getAllServiceRoleList(serviceEntity.getId());
            Map<String, FrameServiceRoleEntity> srvRoleMap = CollectionUtil.toMap(srvRoles, new HashMap<>(), FrameServiceRoleEntity::getServiceRoleName);

            List<ServiceRoleInfo> masterRoles = new ArrayList<>();
            List<ServiceRoleInfo> elseRoles = new ArrayList<>();
            for (ClusterServiceCommandHostCommandEntity hostCommand : context.getCmdHostList(cmd.getCommandId())) {
                FrameServiceRoleEntity frameServiceRoleEntity = srvRoleMap.get(hostCommand.getServiceRoleName());

                ServiceRoleInfo serviceRoleInfo = JSONObject.parseObject(frameServiceRoleEntity.getServiceRoleJson(), ServiceRoleInfo.class);
                serviceRoleInfo.setClusterId(clusterInfo.getId());

                serviceRoleInfo.setHostname(hostCommand.getHostname());
                serviceRoleInfo.setHostCommandId(hostCommand.getHostCommandId());

                serviceRoleInfo.setParentName(cmd.getServiceName());
                serviceRoleInfo.setCommandType(CommandType.ofCode(cmd.getCommandType()));
                serviceRoleInfo.setServiceInstanceId(cmd.getServiceInstanceId());

                serviceRoleInfo.setPackageName(serviceEntity.getPackageName());
                serviceRoleInfo.setArchInfoMap(LoadServiceMeta.getArchInfo(serviceEntity));
                serviceRoleInfo.setDecompressPackageName(serviceEntity.getDecompressPackageName());
                serviceRoleInfo.setFrameCode(serviceEntity.getFrameCode());


                ServiceInfo serviceInfo = JSONObject.parseObject(serviceEntity.getServiceJson(), ServiceInfo.class);
                serviceRoleInfo.setCreateDecompressDir(serviceInfo.getCreateDecompressDir());
                Optional.ofNullable(ServiceRoleStrategyContext.getServiceRoleHandler(serviceRoleInfo.getName()))
                        .ifPresent(ha -> ha.handlerServiceRoleInfo(serviceRoleInfo, hostCommand.getHostname()));


                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType())) {
                    masterRoles.add(serviceRoleInfo);
                } else {
                    elseRoles.add(serviceRoleInfo);
                }
            }

            serviceNode.setMasterRoles(masterRoles);
            serviceNode.setElseRoles(elseRoles);

            NodeDefinitionEntity node = new NodeDefinitionEntity();
            node.setNodeName(srv);
            node.setNodeConfig(JSONObject.toJSONString(serviceNode));
            nodeMap.put(srv, node);
        });


        DagDefinition definition = new DagDefinition();
        definition.setDagName("导入部署清单安装");
        String dagId = dagService.saveDAG(definition);
        dagService.saveNodes(dagId, new ArrayList<>(nodeMap.values()));

        dag.getEdges().forEach(edge -> {
            NodeDefinitionEntity start = nodeMap.get(edge.getStart());
            NodeDefinitionEntity end = nodeMap.get(edge.getEnd());
            dagService.saveEdge(dagId, start, end);
        });

        return dagId;
    }


    private void doInstall(String dagId, boolean restart, List<String> commandIds) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("构建DAG完成，开始执行命令");
                DAGExecCommand cmd = new DAGExecCommand();
                cmd.setDagId(dagId);
                cmd.setRestart(restart);
                ActorRef actor = ActorUtils.getLocalActor(DAGExecActor.class, ActorUtils.getActorRefName(DAGExecActor.class));
                actor.tell(cmd, ActorRef.noSender());
            } catch (Exception e) {
                log.error("execute dagId: {} fail, {}", dagId, e.getMessage(), e);
                transactionalUtils.doInNewTx(() -> commandIds.forEach(cmdId -> updateCommandState(cmdId, CommandState.FAILED)));
            }
        });
    }

    private void updateCommandState(String cmdId, CommandState state) {
        ClusterServiceCommandEntity cmd = commandService.getCommandById(cmdId);
        if (state == CommandState.FAILED) {
            if (Arrays.asList(CommandState.RUNNING, CommandState.WAIT).contains(cmd.getCommandState())) {
                cmd.setCommandState(state);
                commandService.updateById(cmd);
                List<String> cmdHostList = commandHostService.lambdaQuery()
                        .in(ClusterServiceCommandHostEntity::getCommandState, Arrays.asList(CommandState.WAIT, CommandState.RUNNING))
                        .eq(ClusterServiceCommandHostEntity::getCommandId, cmdId)
                        .select(ClusterServiceCommandHostEntity::getCommandHostId)
                        .list()
                        .stream()
                        .map(ClusterServiceCommandHostEntity::getCommandHostId)
                        .collect(Collectors.toList());
                if (!cmdHostList.isEmpty()) {
                    hostCommandService.lambdaUpdate()
                            .in(ClusterServiceCommandHostCommandEntity::getCommandHostId, cmdHostList)
                            .set(ClusterServiceCommandHostCommandEntity::getCommandState, state)
                            .update();
                    commandHostService.lambdaUpdate()
                            .in(ClusterServiceCommandHostEntity::getCommandHostId, cmdHostList)
                            .set(ClusterServiceCommandHostEntity::getCommandState, state)
                            .update();
                }
            }
        }

        if (state == CommandState.RUNNING) {
            if (cmd.getCommandState().equals(CommandState.SUCCESS)) {
                return;
            }
            cmd.setCommandState(state);
            commandService.updateById(cmd);
            List<String> cmdHostList = commandHostService.lambdaQuery()
                    .eq(ClusterServiceCommandHostEntity::getCommandId, cmdId)
                    .select(ClusterServiceCommandHostEntity::getCommandHostId)
                    .list()
                    .stream()
                    .map(ClusterServiceCommandHostEntity::getCommandHostId)
                    .collect(Collectors.toList());
            if (!cmdHostList.isEmpty()) {
                hostCommandService.lambdaUpdate()
                        .in(ClusterServiceCommandHostCommandEntity::getCommandHostId, cmdHostList)
                        .set(ClusterServiceCommandHostCommandEntity::getCommandState, state)
                        .update();
                commandHostService.lambdaUpdate()
                        .in(ClusterServiceCommandHostEntity::getCommandHostId, cmdHostList)
                        .set(ClusterServiceCommandHostEntity::getCommandState, state)
                        .update();
            }
        }
    }

    @Override
    public void redeploy(String dagId) {
        DagDefinition def = dagService.getDagById(dagId);
        if (!Arrays.asList(DagStatus.FAILED, DagStatus.CANCEL).contains(def.getStatus())) {
            throw new BusinessException(String.format("当前任务的状态为%s，不允许重复运行", def.getStatus().name()));
        }
        if (def.getCreatedTime().plusDays(2).isBefore(LocalDateTime.now())) {
            throw new BusinessException("任务已经过期, 不允许在运行");
        }
        List<NodeDefinition> nodes = dagService.getNodesByDagId(dagId, true);
        if (nodes.isEmpty()) {
            return;
        }
        for (NodeDefinition node : nodes) {
            if (!NodeStatus.SUCCESS.equals(node.getStatus())) {
                dagService.updateNodeStatus(node.getId(), NodeStatus.PENDING);
            }
        }
        List<String> commandIds = new ArrayList<>();
        for (NodeDefinition node : nodes) {
            ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);

            if (!node.getStatus().equals(NodeStatus.SUCCESS)) {
                serviceNode.getMasterRoles().forEach(role-> {
                    Integer roleGroupId = (Integer) CacheUtils.get("UseRoleGroup_" + role.getServiceInstanceId());
                    if (roleGroupId == null) {
                        throw new BusinessException("系统已经重启，内存缓存数据已经丢失，当前任务无法恢复，请重写上传部署清单安装");
                    }
                });
            }
            commandIds.add(serviceNode.getCommandId());
        }

        commandIds.forEach(cmd -> updateCommandState(cmd, CommandState.RUNNING));
        doInstall(dagId, true, commandIds);
    }

    @Override
    public InstallProgressDAG2 getDeployProgressDAG2(String dagId) {
        DagDefinition def = dagService.getDagById(dagId);
        InstallProgressDAG2 result = BeanUtil.toBean(def, InstallProgressDAG2.class);

        List<NodeDefinition> nodes = dagService.getNodesByDagId(dagId, true);
        if (nodes.isEmpty()) {
            return result;
        }

        List<InstallProgressDAG2.Node> resultNodes = new ArrayList<>();
        for (NodeDefinition node : nodes) {
            InstallProgressDAG2.Node resultNode = BeanUtil.toBean(node, InstallProgressDAG2.Node.class);
            ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);
            String cmdId = serviceNode.getCommandId();
            resultNode.setCommandId(cmdId);
            List<InstallProgressDAG2.SrvRole> roles = createSrvRole(cmdId);
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

    private List<InstallProgressDAG2.SrvRole> createSrvRole(String cmdId) {
        List<ClusterServiceCommandHostCommandEntity> hostCmdList = hostCommandService.lambdaQuery()
                .eq(ClusterServiceCommandHostCommandEntity::getCommandId, cmdId)
                .list();
        List<InstallProgressDAG2.SrvRole> roles = new ArrayList<>();
        hostCmdList.stream()
                .collect(Collectors.groupingBy(ClusterServiceCommandHostCommandEntity::getServiceRoleName))
                .forEach((roleName, list) -> {
                    InstallProgressDAG2.SrvRole role = new InstallProgressDAG2.SrvRole();
                    role.setRoleName(roleName);

                    List<InstallProgressDAG2.HostCmd> cmds = list.stream()
                            .sorted(Comparator.comparing(ClusterServiceCommandHostCommandEntity::getCreateTime))
                            .map(hostCmd -> {
                                InstallProgressDAG2.HostCmd cmd = BeanUtil.toBean(hostCmd, InstallProgressDAG2.HostCmd.class, CopyOptions.create().setIgnoreProperties("commandState"));
                                if (hostCmd.getCommandState() != null) {
                                    cmd.setCommandState(hostCmd.getCommandState().name());
                                    cmd.setCommandStateName(hostCmd.getCommandState().getDesc());
                                }
                                return cmd;
                            })
                            .collect(Collectors.toList());
                    role.setCmdList(cmds);

                    roles.add(role);
                });

        return roles;
    }

}
