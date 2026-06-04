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

package com.datasophon.api.master;

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

import java.util.Date;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSONObject;

/**
 * K8s DAG 执行器（Spring Service）。
 *
 * <p>合并原 {@code K8SDAGExecActor}（业务逻辑）与 {@code K8SDAGExecService}（@Async 包装），
 * 使用构造器注入代替 {@code SpringUtil.getBean()} 静态查找。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class K8SDAGExecutor {
    
    private final DAGService dagService;
    private final ClusterK8sServiceCommandService commandService;
    
    /**
     * 异步执行 K8s DAG 任务（替代原 {@code K8SDAGExecActor.tell(cmd)}）。
     */
    @Async("masterExecutor")
    public void execK8SDAG(DAGExecCommand cmd) {
        try {
            RepoDAG dag = createMultiServiceDAG(cmd);
            
            NodeTask task = (nodeDef) -> {
                K8sServiceNode serviceNode = JSONObject.parseObject((String) nodeDef.getNodeConfig(), K8sServiceNode.class);
                doExecServiceNode(serviceNode);
                return String.format("%s %s成功",
                        serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName());
            };
            
            dag.exec(task, cmd.isRestart());
        } catch (Throwable e) {
            log.error("K8S DAG execution failed for dagId={}: {}", cmd.getDagId(), e.getMessage(), e);
        }
    }
    
    // ─── private methods ─────────────────────────────────────────────────────
    
    private RepoDAG createMultiServiceDAG(DAGExecCommand cmd) {
        String dagId = cmd.getDagId();
        log.info("K8SDAGExecutor 开始执行 DAG 任务，dagId:{}", dagId);
        DAGRepository repository = dagService;
        RepoDAG dag = new RepoDAG(repository);
        dag.init(dagId, false);
        
        dag.registerListener(new DAGListener() {
            @Override
            public void onNodeSuccess(NodeDefinition node, String result) {
                log.info("DAG 节点执行成功：{}, 结果：{}", node.getNodeName(), result);
                K8sServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), K8sServiceNode.class);
                updateCmdState(serviceNode, CommandState.SUCCESS);
            }
            
            @Override
            public void onNodeFail(NodeDefinition node, Throwable throwable) {
                log.error("DAG 节点执行失败：{}, 错误：{}", node.getNodeName(), throwable.getMessage());
                K8sServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), K8sServiceNode.class);
                updateCmdState(serviceNode, CommandState.FAILED);
            }
            
            @Override
            public void onNodeCancel(NodeDefinition node, Throwable throwable) {
                log.warn("DAG 节点执行取消：{}, 原因：{}", node.getNodeName(), throwable.getMessage());
                K8sServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), K8sServiceNode.class);
                updateCmdState(serviceNode, CommandState.CANCEL);
            }
        });
        return dag;
    }
    
    private void updateCmdState(K8sServiceNode serviceNode, CommandState commandState) {
        log.info("更新{}{}的状态为{}",
                serviceNode.getCommandType().getCommandName(Constants.CN),
                serviceNode.getServiceName(), commandState);
        commandService.lambdaUpdate()
                .eq(ClusterK8sServiceCommandEntity::getCommandId, serviceNode.getCommandId())
                .set(ClusterK8sServiceCommandEntity::getCommandState, commandState)
                .set(ClusterK8sServiceCommandEntity::getCommandProgress, 100)
                .set(ClusterK8sServiceCommandEntity::getEndTime, new Date())
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
            ExecResult result = handler.invoke(serviceNode);
            if (!result.isSuccess()) {
                log.error("{}服务{}失败，{}", type.getCommandName(Constants.CN),
                        serviceNode.getServiceName(), result.getErrorTraceMessage());
                throw new BusinessHintException(String.format("%s服务%s失败，%s",
                        type.getCommandName(Constants.CN), serviceNode.getServiceName(), result.getErrorTraceMessage()));
            }
        } catch (BusinessHintException e) {
            throw e;
        } catch (Exception e) {
            log.error("{}服务{}失败，{}", type.getCommandName(Constants.CN),
                    serviceNode.getServiceName(), e.getMessage(), e);
            throw new IllegalStateException(String.format("%s服务%s失败，%s",
                    type.getCommandName(Constants.CN), serviceNode.getServiceName(), e.getMessage()), e);
        }
    }
}
