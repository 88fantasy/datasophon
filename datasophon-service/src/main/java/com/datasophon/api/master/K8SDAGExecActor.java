package com.datasophon.api.master;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.dag.DAGListener;
import com.datasophon.api.dag.NodeTask;
import com.datasophon.api.dag.RepoDAG;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.api.dag.repo.DAGRepository;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.master.handler.k8s.ApplyServiceHandler;
import com.datasophon.api.master.handler.k8s.RestartServiceHandler;
import com.datasophon.api.master.handler.k8s.ServiceHandler;
import com.datasophon.api.master.handler.k8s.StopServiceHandler;
import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.common.Constants;
import com.datasophon.common.command.dag.DAGExecCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.k8s.K8sServiceNode;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;
import com.datasophon.dao.enums.CommandState;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class K8SDAGExecActor extends TypedActor<DAGExecCommand> {


    @Override
    protected void doOnReceive(DAGExecCommand message) throws Throwable {
        RepoDAG dag = createMultiServiceDAG(message);

        NodeTask task = (nodeDef) -> {
//            单个服务的安装
            K8sServiceNode serviceNode = JSONObject.parseObject((String) nodeDef.getNodeConfig(), K8sServiceNode.class);
            doExecServiceNode(serviceNode);

            return String.format("%s %s成功", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName());
        };

        dag.exec(task, message.isRestart());
    }


    private RepoDAG createMultiServiceDAG(DAGExecCommand cmd) {
        String dagId = cmd.getDagId();
        log.info("K8SDAGExecActor开始执行任务， id:{}", dagId);
        DAGRepository repository = SpringUtil.getBean(DAGService.class);
        RepoDAG dag = new RepoDAG(repository);
        dag.init(dagId, false);

        dag.registerListener(new DAGListener() {
            @Override
            public void onNodeSuccess(NodeDefinition node, String result) {
                K8sServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), K8sServiceNode.class);
                updateCmdState(serviceNode, CommandState.SUCCESS);
            }

            @Override
            public void onNodeFail(NodeDefinition node, Throwable throwable) {
                K8sServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), K8sServiceNode.class);
                updateCmdState(serviceNode, CommandState.FAILED);
            }

            @Override
            public void onNodeCancel(NodeDefinition node, Throwable throwable) {
                K8sServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), K8sServiceNode.class);
                updateCmdState(serviceNode, CommandState.CANCEL);
            }
        });
        return dag;
    }

    private void updateCmdState(K8sServiceNode serviceNode, CommandState commandState) {
        log.info("更新{}{}的状态为{}", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), commandState);
        ClusterK8sServiceCommandService commandService = getBean(ClusterK8sServiceCommandService.class);
        commandService.lambdaUpdate()
                .eq(ClusterK8sServiceCommandEntity::getCommandId, serviceNode.getCommandId())
                .set(ClusterK8sServiceCommandEntity::getCommandState, commandState)
                .update();
    }

    private void doExecServiceNode(K8sServiceNode serviceNode) {
        CommandType type = serviceNode.getCommandType();
        ServiceHandler handler;
        switch (type) {
            case INSTALL_SERVICE:
            case UPGRADE_SERVICE:
            case START_SERVICE:
                handler = new ApplyServiceHandler(type);
                break;
            case STOP_SERVICE:
                handler = new StopServiceHandler();
                break;
            case RESTART_SERVICE:
                handler = new RestartServiceHandler();
                break;
            default:
                throw new BusinessException(String.format("unknown cmd type: %s of srv %s in namespace %s",
                        type.getCommandName(Constants.CN), serviceNode.getServiceName(), serviceNode.getNamespace()));
        }
        try {
            ExecResult result = handler.handlerRequest(serviceNode);
            if (!result.isSuccess()) {
                log.error("{}服务{}失败，{}", type.getCommandName(Constants.CN), serviceNode.getServiceName(), result.getErrorTraceMessage());
                throw new BusinessHintException(String.format("%s服务%s失败，%s", type.getCommandName(Constants.CN), serviceNode.getServiceName(), result.getErrorTraceMessage()));
            }
        } catch (BusinessHintException e) {
            throw e;
        } catch (Exception e) {
            log.error("{}服务{}失败，{}", type.getCommandName(Constants.CN), serviceNode.getServiceName(), e.getMessage(), e);
            throw new IllegalStateException(String.format("%s服务%s失败，%s", type.getCommandName(Constants.CN), serviceNode.getServiceName(), e.getMessage()), e);
        }

    }


}
