package com.datasophon.api.service.extrepo.impl;

import akka.actor.ActorRef;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.load.LoadServiceMeta;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.master.SubmitTaskNodeActor;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.ClusterServiceCommandHostService;
import com.datasophon.api.service.ClusterServiceCommandService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.api.service.extrepo.ExtRepoInstallService;
import com.datasophon.api.service.extrepo.ctx.DeploymentDAGBuildContext;
import com.datasophon.api.service.extrepo.ctx.ExecDAGBuilderContext;
import com.datasophon.api.service.extrepo.utils.MetaUtils;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.strategy.ServiceRoleStrategyContext;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.vo.extrepo.DeploymentDAG;
import com.datasophon.api.vo.extrepo.InstallProgressDAG;
import com.datasophon.common.Constants;
import com.datasophon.common.command.SubmitActiveTaskNodeCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.DAG;
import com.datasophon.common.model.DAGGraph;
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
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.enums.ServiceState;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> deploy(DeploymentDTO dto) {
        File deploymentFile = uploadTempFileService.getTempFile(dto.getDeployFileId());
        if (deploymentFile == null) {
            throw new BusinessException("部署清单文件不存在");
        }
        String content = MetaUtils.decodeFile(deploymentFile, dto.getContentDecodePasswd());
        DeploymentModel model = MetaUtils.parseDeploymentFile(content);
        log.debug("解析到配置\n：{}", JSONObject.toJSONString(model, true));
        log.info("完成解析部署文件, 需要部署{}个应用", model.getApp().size());


        Set<String> deployHosts = model.getApp()
                .stream()
                .flatMap(app -> app.getRoles().stream())
                .flatMap(role -> role.getDeployHosts().stream())
                .collect(Collectors.toSet());
        List<ClusterHostDO> hostList = clusterHostService.getHostListByClusterId(dto.getClusterId());
        Map<String, ClusterHostDO> hostMap = hostList.stream().collect(Collectors.toMap(ClusterHostDO::getHostname, a -> a, (a, b) -> a));
        deployHosts = deployHosts.stream().filter(host -> !hostMap.containsKey(host)).collect(Collectors.toSet());
        if (!deployHosts.isEmpty()) {
            throw new BusinessException(String.format("以下主机%s不存在或者无法通讯", StrUtil.join(",", deployHosts)));
        }

        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());
        DeploymentDAGBuildContext ctx = new DeploymentDAGBuildContext(clusterInfo, serviceList);
        List<String> errors = new ArrayList<>();
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
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

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
            List<ServiceConfig> configs = serviceInstallService.getServiceConfigOption(dto.getClusterId(), app.getName());
            Map<String, DeploySrvConfig> configMap = CollectionUtil.toMap(app.getConfig(), new HashMap<>(), DeploySrvConfig::getName);
            configs.forEach(conf -> {
                DeploySrvConfig deployConf = configMap.get(conf.getName());
                if (deployConf == null) {
                    conf.setValue(conf.getDefaultValue());
                } else {
                    conf.setValue(deployConf.getValue());
                }
            });
            serviceInstallService.saveServiceConfig(dto.getClusterId(), app.getName(), configs, null);
        });
        log.info("保存部署配置项成功");


        Map<String, FrameServiceEntity> srvDefMap = CollectionUtil.toMap(serviceList, new HashMap<>(), srv -> srv.getServiceName() + ":" + srv.getServiceVersion());
        List<String> commandIds = new ArrayList<>(model.getApp().size());
        model.getApp().forEach(srv -> {
            String cmdId = generateCommand(clusterInfo, hostMappings, srvDefMap.get(srv.getName() + ":" + srv.getVersion()));
            commandIds.add(cmdId);
        });
        log.info("保存安装命令成功, 共需要安装{}个应用", commandIds.size());


        DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag = ctx.buildDeployDAG(model.getApp(), false);
//        必须等待事务提交，否则，有概率查询不到保存的命令
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        doInstall(clusterInfo.getId(), commandIds, dag);
                    } catch (Exception e) {
                        commandService.lambdaUpdate()
                                .in(ClusterServiceCommandEntity::getCommandId, commandIds)
                                .set(ClusterServiceCommandEntity::getCommandState, CommandState.FAILED)
                                .update();
                        log.error("execute command: {} fail, {}", StrUtil.join(",", commandIds), e.getMessage(), e);
                    }
                });
            }
        });
        return commandIds;
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
        log.info("命令:{}保存各主机总命令信息成功,共涉及{}台主机", cmd.getCommandId(), hostEntityList.size());


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
        log.info("命令:{}保存各主机需要执行命令成功,共需要执行{}个命令", cmd.getCommandId(), hostCommandList.size());

        return cmd.getCommandId();
    }


    private void doInstall(Integer clusterId, List<String> commandIds, DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag) {
        log.info("开始执行安装操作");
        SubmitActiveTaskNodeCommand activeTaskNodeCmd = new SubmitActiveTaskNodeCommand();
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        activeTaskNodeCmd.setClusterId(clusterInfo.getId());
        activeTaskNodeCmd.setClusterCode(clusterInfo.getClusterCode());

//        设置准备就绪，可以安装的服务
        Collection<String> beginNode = dag.getBeginNode();
        Map<String, String> readyToSubmitTaskList = new ConcurrentHashMap<>();
        for (String node : beginNode) {
            readyToSubmitTaskList.put(node, "");
        }
        activeTaskNodeCmd.setReadyToSubmitTaskList(readyToSubmitTaskList);


        ExecDAGBuilderContext context = new ExecDAGBuilderContext();
        context.setSrvCmd(commandService.lambdaQuery().in(ClusterServiceCommandEntity::getCommandId, commandIds).list());
        context.setCmdHost(hostCommandService.lambdaQuery().in(ClusterServiceCommandHostCommandEntity::getCommandId, commandIds).list());


//        将服务依赖的dag重构成节点安装服务的dag
        DAGGraph<String, ServiceNode, String> deployGAG = new DAGGraph<>();
        dag.getNodes().forEach((srv, info) -> {
            ServiceNode serviceNode = new ServiceNode();
            ClusterServiceCommandEntity cmd = context.getCmd(srv);
            serviceNode.setCommandId(cmd.getCommandId());
            serviceNode.setCommandType(CommandType.ofCode(cmd.getCommandType()));

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
            deployGAG.addNode(srv, serviceNode);
        });

        dag.getEdges().forEach(edge -> deployGAG.addEdge(edge.getStart(), edge.getEnd(), false));
        activeTaskNodeCmd.setDag(deployGAG);
        log.debug("开始执行dag, submitActiveTaskNodeCommand:{}", JSONObject.toJSONString(activeTaskNodeCmd));


        log.info("构建DAG完成，开始执行命令");
        ActorRef submitTaskNodeActor = ActorUtils.getLocalActor(SubmitTaskNodeActor.class, ActorUtils.getActorRefName(SubmitTaskNodeActor.class));
        submitTaskNodeActor.tell(activeTaskNodeCmd, ActorRef.noSender());
    }


    @Override
    public InstallProgressDAG getDeployProgressDAG(Integer clusterId, List<String> cmdIds) {
        List<ClusterServiceCommandEntity> cmdList = commandService.lambdaQuery().in(ClusterServiceCommandEntity::getCommandId, cmdIds).list();

        List<ClusterServiceCommandHostCommandEntity> hostCmdList = hostCommandService.lambdaQuery()
                .in(ClusterServiceCommandHostCommandEntity::getCommandId, cmdIds)
                .list();
        Map<String, List<ClusterServiceCommandHostCommandEntity>> hostCmdMap = hostCmdList.stream().collect(
                Collectors.groupingBy(ClusterServiceCommandHostCommandEntity::getCommandId)
        );

        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());
        DeploymentDAGBuildContext ctx = new DeploymentDAGBuildContext(clusterInfo, serviceList);


        DAG<String, InstallProgressDAG.Srv, Integer> dag = new DAG<>();
        for (int i = 0; i < cmdList.size(); i++) {
            ClusterServiceCommandEntity cmd = cmdList.get(i);
            InstallProgressDAG.Srv srv = newSrv(cmd, i, hostCmdMap.getOrDefault(cmd.getCommandId(), new ArrayList<>()));
            dag.addNode(srv.getName(), srv);
        }

        int edgeIdCounter = 0;

        for (int i = 0; i < cmdList.size(); i++) {
            ClusterServiceCommandEntity start = cmdList.get(i);
            Set<String> dependencies = ctx.getDependencies(start.getServiceName());

            for (int j = 0; j < cmdList.size(); j++) {
//                不能自依赖
                if (i == j) {
                    continue;
                }
                ClusterServiceCommandEntity end = cmdList.get(j);
                if (dependencies.contains(end.getServiceName())) {
                    List<String> path = dag.findPath(end.getServiceName(), start.getServiceName());
                    if (path == null) {
                        dag.addEdge(start.getServiceName(), end.getServiceName(), edgeIdCounter++, false);
                    }
                }
            }
        }


        DAG<String, InstallProgressDAG.Srv, Integer> reverseDag = dag.getReverseDag();
        InstallProgressDAG result = new InstallProgressDAG();
        reverseDag.getNodes().values().forEach(node -> result.getSrvList().add(node));
        reverseDag.getEdges().forEach(edge -> {
            InstallProgressDAG.EdgeVO vo = new InstallProgressDAG.EdgeVO();
            vo.setId(edge.getEdge());
            vo.setStart(reverseDag.getNode(edge.getStart()).getId());
            vo.setEnd(reverseDag.getNode(edge.getEnd()).getId());
            result.getEdge().add(vo);
        });
        return result;
    }

    private InstallProgressDAG.Srv newSrv(ClusterServiceCommandEntity cmd, int i, List<ClusterServiceCommandHostCommandEntity> hostCmdList) {
        InstallProgressDAG.Srv srv = new InstallProgressDAG.Srv();
        srv.setId(i);
        srv.setName(cmd.getServiceName());
        srv.setCmdId(cmd.getCommandId());
        srv.setCommandState(cmd.getCommandState());


        List<InstallProgressDAG.SrvRole> roles = new ArrayList<>();
        hostCmdList.stream()
                .collect(Collectors.groupingBy(ClusterServiceCommandHostCommandEntity::getServiceRoleName))
                .forEach((roleName, list) -> {
                    InstallProgressDAG.SrvRole role = new InstallProgressDAG.SrvRole();
                    role.setRoleName(roleName);

                    List<InstallProgressDAG.HostCmd> cmds = list.stream()
                            .sorted(Comparator.comparing(ClusterServiceCommandHostCommandEntity::getCreateTime))
                            .map(hostCmd -> {
                                return BeanUtil.toBean(hostCmd, InstallProgressDAG.HostCmd.class);
                            })
                            .collect(Collectors.toList());
                    role.setCmdList(cmds);

                    roles.add(role);
                });

        srv.setRoles(roles);
        return srv;
    }

}
