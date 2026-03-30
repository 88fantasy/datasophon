package com.datasophon.api.service.extrepo.impl;

import akka.actor.ActorRef;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.dag.model.DagDefinition;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.K8sProductDeployMapping;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesSaveDTO;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.master.K8SDAGExecActor;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.api.service.extrepo.K8sProductInstallService;
import com.datasophon.api.service.extrepo.ctx.ServiceDAGBuilder;
import com.datasophon.api.service.extrepo.vo.K8sCommandNode;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.TransactionalUtils;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.api.vo.instance.K8sServiceInstanceValuesVO;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.dag.DAGExecCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.DAG;
import com.datasophon.common.model.k8s.K8sServiceNode;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;
import com.datasophon.dao.entity.dag.NodeDefinitionEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.model.extrepo.DeploySrvModel;
import com.datasophon.dao.model.extrepo.DeploymentModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 */
@Component("k8SProductInstallService")
@Slf4j
public class K8SProductInstallServiceImpl extends ProductDeployHandlerSupport implements K8sProductInstallService {


    @Autowired
    private FrameK8sServiceService frameK8sServiceService;

    @Autowired
    private K8sServiceInstanceValuesService k8sServiceInstanceValuesService;

    @Autowired
    private K8sServiceInstanceService k8sServiceInstanceService;

    @Autowired
    private ClusterK8sServiceCommandService k8sServiceCommandService;

    @Autowired
    private TransactionalUtils transactionalUtils;

    @Autowired
    private K8sClusterNamespaceService k8sClusterNamespaceService;

    public static final String K8S_SERVICE_NAMESPACE_MAPPING = "k8s_service_namespace_mapping";

    @Override
    public ValidateResultVO validateDeploymentModel(DeploymentModel model, DeploymentDTO dto) {
        List<String> errors = new ArrayList<>();
        List<DeploySrvModel> apps = getTargetApps(model);

        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<FrameK8sServiceEntity> serviceList = frameK8sServiceService.getByFrameCode(clusterInfo.getClusterFrame());
        Map<String, FrameK8sServiceEntity> map = serviceList.stream().collect(Collectors.toMap(
                e -> e.getServiceName() + ":" + e.getServiceVersion(),
                e -> e,
                (a, b) -> a
        ));
        apps.forEach(app -> {
            FrameK8sServiceEntity entity = map.get(app.getName() + ":" + app.getVersion());
            if (entity == null) {
                errors.add(String.format("服务%s %s不存在", app.getName(), app.getVersion()));
            }
        });

        if (errors.isEmpty()) {
            ValidateResultVO vo = new ValidateResultVO();
            List<ValidateResultVO.DeployK8sServiceModel> services = new ArrayList<>();
            apps.forEach(app -> {
                app.getRoles().forEach(role -> {
                    ValidateResultVO.DeployK8sServiceModel tmp = new ValidateResultVO.DeployK8sServiceModel();
                    tmp.setServiceName(app.getName());
                    tmp.setVersion(app.getVersion());
                    tmp.setNamespace(app.getNamespace());
                    tmp.setMetaFileType(app.getMetaFileType().toLowerCase());
                    services.add(tmp);
                });
            });
            vo.setK8sServices(services);
            return vo;
        } else {
            return new ValidateResultVO(errors);
        }
    }

    @Override
    public InstallResult deploy(DeploymentDTO dto) {
        DeploymentModel model = doParseDeploymentFile(dto);
        log.debug("解析到配置\n：{}", JSONObject.toJSONString(model, true));
        List<DeploySrvModel> apps = getTargetApps(model);
        log.info("完成解析部署文件，总共需要部署{}个应用", apps.size());

//        检查参数
        Map<String, K8sCommandNode> installContext = new HashMap<>();

//        检查服务的合法性
        List<FrameK8sServiceEntity> serviceList = frameK8sServiceService.listNewest(dto.getClusterId());
        Map<String, FrameK8sServiceEntity> srvDefMap = CollectionUtil.toMap(serviceList, new HashMap<>(), FrameK8sServiceEntity::getServiceName);
        apps.forEach(srv -> {
            FrameK8sServiceEntity frameService = srvDefMap.get(srv.getName());
            if (frameService == null) {
                throw new RuntimeException(String.format("服务%s定义不存在", srv.getName()));
            } else if (frameService.getServiceVersion().equals(srv.getVersion())) {
                throw new RuntimeException(String.format("服务%s不是当前的最新版本", srv.getName()));
            }

            K8sCommandNode node = new K8sCommandNode();
            node.setService(frameService);
            installContext.put(srv.getName(), node);
        });

//        1. 保存 namespace 映射
        List<K8sProductDeployMapping> mappings = new ArrayList<>();
        apps.forEach(app -> {
            K8sProductDeployMapping mapping = new K8sProductDeployMapping();
            mapping.setServiceName(app.getName());
            mapping.setNamespace(app.getNamespace());
            mapping.setMetaFileType(app.getMetaFileType().toLowerCase());
            mappings.add(mapping);

            installContext.get(app.getName()).setMapping(mapping);
        });
        log.info("保存服务 namespace 映射成功，共{}个映射", mappings.size());


//        保存运行时变量
        mappings.forEach(mapping-> {
            K8sCommandNode node = installContext.get(mapping.getServiceName());
            Integer serviceId = node.getService().getId();
            K8sServiceInstanceValuesSaveDTO values = new K8sServiceInstanceValuesSaveDTO();
            values.setClusterId(dto.getClusterId());
            values.setNamespace(mapping.getNamespace());
            values.setServiceId(serviceId);

            K8sServiceInstanceValuesVO content = k8sServiceInstanceValuesService.getValueFromRepo(serviceId, mapping.getMetaFileType());
            values.setValues(content.getValues());
            values.setDeltaValues(content.getDeltaValues());
            node.setValueId(saveConfigValues(values));
        });


//        新增安装命令
        List<ClusterK8sServiceCommandEntity> commands = new ArrayList<>(apps.size());
        apps.forEach(srv -> {
            FrameK8sServiceEntity frameService = srvDefMap.get(srv.getName());
            ClusterK8sServiceCommandEntity cmd = doGenerateInstallCmd(dto.getClusterId(), srv, frameService);

            K8sCommandNode node = installContext.get(srv.getName());
            node.setCmd(cmd);
            commands.add(cmd);
        });
        log.info("保存 K8s 安装命令成功，共需要安装{}个应用", commands.size());
        DAG<String, K8sCommandNode, Integer> dag = new ServiceDAGBuilder<K8sCommandNode>().buildDeployDAG(new ArrayList<>(installContext.values()));

//        保存dag
        List<String> commandIds = commands.stream().map(ClusterK8sServiceCommandEntity::getCommandId).collect(Collectors.toList());
        String dagId = saveDAG(dto.getClusterId(), "部署K8S制品", dag);

//        必须等待事务提交，否则，有概率查询不到保存的命令
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invokeCommands(dagId, commandIds);
            }
        });
        return new InstallResult(dagId);
    }

    @Override
    public void redeploy(RunDagDto dto) {

    }

    @Override
    public String generateGenericInstallCommand(Integer clusterId, List<String> serviceNames) {
        return "";
    }

    @Override
    public String generateAndExecSrvInstCmd(Integer clusterId, CommandType commandType, List<Integer> serviceInstanceIds) {
        return "";
    }


    @Override
    public List<FrameK8sServiceEntity> listNewestByDeployment(DeploymentDTO dto) {
        DeploymentModel model = doParseDeploymentFile(dto);
        Map<String, DeploySrvModel> map = CollectionUtil.toMap(getTargetApps(model), new HashMap<>(), DeploySrvModel::getName);
        List<FrameK8sServiceEntity> list = frameK8sServiceService.listNewest(dto.getClusterId());
        list.forEach(et -> {
            DeploySrvModel srv = map.get(et.getServiceName());
            if (srv != null) {
                et.setSelected(true);
                et.setMetaFileType(srv.getMetaFileType().toLowerCase());
                et.setNamespace(srv.getNamespace());
            }
        });
        return list;
    }

    @Override
    public void saveServiceNamespaceMapping(Integer clusterId, List<K8sProductDeployMapping> mappings) {
        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        String cacheKey = String.format("%s_%s", cluster.getClusterCode(), K8S_SERVICE_NAMESPACE_MAPPING);
        Map<String, K8sProductDeployMapping> map = CacheUtils.computeIfAbsent(cacheKey, k -> new ConcurrentHashMap<>());
        for (K8sProductDeployMapping mapping : mappings) {
            ProcessUtils.generateClusterVariable(clusterId, mapping.getServiceName(),
                    String.format("%s.%s", mapping.getServiceName(), GlobalVariables.NAMESPACE), mapping.getNamespace());
            map.put(mapping.getServiceName(), mapping);
        }
    }

    @Override
    public Integer saveConfigValues(K8sServiceInstanceValuesSaveDTO dto) {
        FrameK8sServiceEntity service = frameK8sServiceService.getById(dto.getServiceId());
        K8sServiceInstanceValues values = k8sServiceInstanceValuesService.save(dto);
        CacheUtils.put("k8sServiceValues_" + service.getServiceName(), values.getId());
        return values.getId();
    }


    private List<DeploySrvModel> getTargetApps(DeploymentModel model) {
        return model.getApp()
                .stream()
                .filter(app -> app.getDeployType().equalsIgnoreCase("K8S"))
                .collect(Collectors.toList());
    }

    /**
     * 生成 K8s 服务安装命令
     */
    private ClusterK8sServiceCommandEntity doGenerateInstallCmd(Integer clusterId, DeploySrvModel deployInfo, FrameK8sServiceEntity frameService) {
        K8sClusterNamespace ns = k8sClusterNamespaceService.getNamespace(new K8sNamespaceIdentityDTO(clusterId, deployInfo.getNamespace()));
        K8sServiceInstance instance = k8sServiceInstanceService.createIfAbsent(clusterId, ns.getId(), frameService.getId());

        CommandType commandType = CommandType.INSTALL_SERVICE;
        ClusterK8sServiceCommandEntity cmd = new ClusterK8sServiceCommandEntity();
        cmd.setCommandId(IdUtil.simpleUUID());
        cmd.setClusterId(clusterId);
        cmd.setCommandName(commandType.getCommandName(PropertyUtils.getString(Constants.LOCALE_LANGUAGE)) + Constants.SPACE + frameService.getServiceName());
        cmd.setCommandProgress(0);
        cmd.setCommandState(CommandState.RUNNING);
        cmd.setCommandType(commandType.getValue());
        cmd.setCreateTime(new Date());
        cmd.setCreateBy("admin");
        cmd.setServiceName(frameService.getServiceName());
        cmd.setServiceInstanceId(instance.getId());
        cmd.setNamespace(ns.getNamespace());
        k8sServiceCommandService.save(cmd);
        log.info("保存 K8s 服务{}的安装命令成功，命令 ID:{}", frameService.getServiceName(), cmd.getCommandId());
        return cmd;
    }


    /**
     * 保存 DAG 定义
     */
    private String saveDAG(Integer clusterId, String serviceActionName, DAG<String, K8sCommandNode, Integer> dag) {
        Map<String, NodeDefinitionEntity> nodeMap = new HashMap<>();
        dag.getNodes().forEach((serviceName, info) -> {
            NodeDefinitionEntity node = new NodeDefinitionEntity();
            node.setNodeName(serviceName);
            node.setNodeConfig(createNodeConfig(info));
            nodeMap.put(serviceName, node);
        });

        DagDefinition definition = new DagDefinition();
        definition.setDagName(String.format("%s服务-%s", serviceActionName, DateUtil.format(new Date(), DatePattern.NORM_DATETIME_PATTERN)));

        StringBuilder sb = new StringBuilder();
        sb.append(serviceActionName).append("服务:");
        dag.getNodes().forEach((srv, n) -> sb.append(srv).append(";"));
        String desc = sb.toString();
        definition.setDescription(desc.length() > 300 ? desc.substring(0, 300) + "..." : desc);
        String dagId = dagService.saveDAG(clusterId, definition);
        dagService.saveNodes(dagId, new ArrayList<>(nodeMap.values()));

        dag.getEdges().forEach(edge -> {
            NodeDefinitionEntity start = nodeMap.get(edge.getStart());
            NodeDefinitionEntity end = nodeMap.get(edge.getEnd());
            dagService.saveEdge(dagId, start, end);
        });

        return dagId;
    }

    private String createNodeConfig(K8sCommandNode info) {
        ClusterK8sServiceCommandEntity cmd = info.getCmd();
        FrameK8sServiceEntity service = info.getService();
        K8sServiceNode node = new K8sServiceNode();
        node.setCommandId(cmd.getCommandId());
        node.setCommandType(CommandType.ofCode(cmd.getCommandType()));
        node.setServiceName(service.getServiceName());
        node.setServiceInstanceId(cmd.getServiceInstanceId());
        node.setNamespace(cmd.getNamespace());

        if (info.getMapping() != null) {
            node.setMetaFileType(info.getMapping().getMetaFileType());
        }
        node.setValueId(info.getValueId());

        return JSONObject.toJSONString(node);
    }

    /**
     * 调用 K8sDAGExecActor 执行命令
     */
    private void invokeCommands(String dagId, List<String> commandIds) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("构建 K8s DAG 完成，开始执行命令");
                DAGExecCommand cmd = new DAGExecCommand();
                cmd.setDagId(dagId);
                cmd.setRestart(false);
                ActorRef actor = ActorUtils.getLocalActor(K8SDAGExecActor.class, ActorUtils.getActorRefName(K8SDAGExecActor.class));
                actor.tell(cmd, ActorRef.noSender());
            } catch (Exception e) {
                log.error("执行 K8s dagId: {} 失败，{}", dagId, e.getMessage(), e);
                transactionalUtils.doInNewTx(() -> commandIds.forEach(cmdId -> updateCommandState(cmdId, CommandState.FAILED)));
            }
        });
    }

    /**
     * 更新命令状态
     */
    private void updateCommandState(String cmdId, CommandState state) {
        ClusterK8sServiceCommandEntity cmd = k8sServiceCommandService.getCommandById(cmdId);
        if (CommandState.FAILED == state) {
            if (Arrays.asList(CommandState.RUNNING, CommandState.WAIT).contains(cmd.getCommandState())) {
                cmd.setCommandState(state);
                cmd.setCommandProgress(100);
                k8sServiceCommandService.updateById(cmd);
            }
        }

        if (CommandState.RUNNING == state) {
            cmd.setCommandState(state);
            cmd.setCommandProgress(0);
            k8sServiceCommandService.updateById(cmd);
        }
    }
}
