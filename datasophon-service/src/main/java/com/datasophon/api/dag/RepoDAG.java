package com.datasophon.api.dag;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.api.CancelException;
import com.datasophon.api.dag.model.EdgeDefinition;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.api.dag.repo.DAGRepository;
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.enums.dag.NodeStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author zhanghuangbin
 */
@Data
@Slf4j
public class RepoDAG {
    private String dagId;

    protected List<DAGListener> listeners = new ArrayList<>();

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private boolean revered = false;

    private final DAGRepository repository;

    public RepoDAG(DAGRepository repository) {
        this.repository = repository;
    }


    public void init(String dagId, boolean revered) {
        this.dagId = dagId;
        listeners.clear();
        isRunning.set(false);
        this.revered = revered;

        Map<String, NodeDefinition> nodes = getNodeAsMap(dagId, false);
        List<EdgeDefinition> definitions = getEdges(dagId);

//        检查是否存在环
        Map<String, List<String>> dependencies = new HashMap<>();
        for (EdgeDefinition edge : definitions) {
            String fromNodeId = edge.getFromNodeId();
            String toNodeId = edge.getToNodeId();
            if (!nodes.containsKey(fromNodeId) || !nodes.containsKey(toNodeId)) {
                throw new IllegalStateException(String.format("ID为%s或者%s的节点不存在", fromNodeId, toNodeId));
            }

            List<String> paths = findPath(dependencies, toNodeId, fromNodeId);
            if (paths.isEmpty()) {
                dependencies.computeIfAbsent(fromNodeId, k -> new ArrayList<>()).add(toNodeId);
            } else {
                paths.add(fromNodeId);
                if (revered) {
                    Collections.reverse(paths);
                }
                throw new IllegalStateException(String.format("有向图存在循环依赖，%s", StrUtil.join("->", paths)));
            }
        }
    }


    public void exec(AsyncNodeTask task) {
        try {
            start();
            Set<String> readNodes = getReadNodes();
            if (readNodes.isEmpty()) {
                throw new IllegalStateException("未找到状态是就绪的任务，请检查任务状态");
            }
            Queue<String> queue = new ArrayDeque<>(readNodes);
            forward(task, queue, null);
        } catch (Throwable throwable) {
            handleDagFailure(throwable);
        }
    }

    public void exec(NodeTask task) {
        try {
            start();
            AsyncNodeTask wrappedTask = (node, callback) -> {
                try {
                    String result = task.exec(node);
                    callback.onSuccess(result);
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            };
            exec(wrappedTask);
        } catch (Throwable throwable) {
            handleDagFailure(throwable);
        }
    }


    public void forward(AsyncNodeTask task, Queue<String> queue, Throwable throwable) {
        if (throwable != null) {
            cancel(throwable);
        } else if (isDone()) {
            finish();
        } else {
            if (queue.isEmpty()) {
                log.warn("dag is not done with an empty visit queue, maybe has a circle");
            }
            while (!queue.isEmpty()) {
                String node = queue.poll();
                NodeDefinition definition = repository.getNodeById(node);
                if (!definition.getStatus().equals(NodeStatus.PENDING)) {
                    continue;
                }
                startNode(node);
                task.exec(definition, new NodeExecutionCallback() {
                    @Override
                    public void onSuccess(String result) {
//                        后继节点入度减1
                        endNode(node, NodeStatus.SUCCESS, result, null);
                        Set<String> successors = getReadySuccessors(node);
                        queue.addAll(successors);
                        forward(task, queue, null);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        endNode(node, NodeStatus.FAILED, null, throwable);
                        forward(task, queue, throwable);
                    }
                });
            }
        }
    }


    private void handleDagFailure(Throwable e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        log.error("exec dag {} fail, ", dagId, e);
        cancel(e);
        throw new RuntimeException(String.format("exec dag %s fail, %s", dagId, e.getMessage()), e);
    }

    public void start() {
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("DAG is already running");
        }
        for (DAGListener listener : getCombineListeners()) {
            listener.onStart(this);
        }
        repository.doInNewTransactional(() -> {
            repository.updateDagStatus(dagId, DagStatus.RUNNING);
            repository.markNodesPending(dagId, true);
        });
    }

    public void startNode(String nodeId) {

        NodeDefinition node = repository.getNodeById(nodeId);
        node.setStatus(NodeStatus.RUNNING);
        node.setStartedTime(LocalDateTime.now());
        for (DAGListener listener : getCombineListeners()) {
            listener.onNodeStarted(node);
        }
        repository.doInNewTransactional(() -> {
            repository.updateNode(node);
        });
    }

    public void endNode(String nodeId, NodeStatus status, String result, Throwable throwable) {
        NodeDefinition node = repository.getNodeById(nodeId);
        node.setStatus(status);
        node.setCompletedTime(LocalDateTime.now());
        if (throwable != null) {
            node.setExecutionLog(throwable.getMessage());
        } else {
            node.setExecutionLog(result);
        }
        for (DAGListener listener : getCombineListeners()) {
            listener.onNodeCompleted(node, status, result, throwable);
        }
        repository.doInNewTransactional(() -> {
            repository.updateNode(node);
        });
    }

    public void cancel(Throwable throwable) {
        Throwable failCause = throwable;
        if (throwable instanceof CancelException) {
            CancelException ex = (CancelException) throwable;
            failCause = ex.getCause();
//            failCause为空，表示主动取消，打印info日志
            if (failCause == null) {
                log.info("dag: {} cancel due to {}", dagId, ex.getMessage());
            } else {
                log.error("dag: {} cancel due to {}", dagId, ex.getMessage(), ex);
            }
        } else {
            log.error("dag: {} stop due to exception, {}", dagId, throwable.getMessage(), throwable);
        }

        if (isRunning.compareAndSet(true, false)) {
//            如果因为失败而取消，则标记为失败
            DagStatus dagStatus = failCause != null ? DagStatus.FAILED : DagStatus.CANCEL;
            for (DAGListener listener : getCombineListeners()) {
                listener.onCompleted(this, dagStatus, failCause);
            }
            repository.doInNewTransactional(() -> repository.updateDagStatus(dagId, dagStatus));

            Throwable cause = failCause;
            Map<String, NodeDefinition> nodes = getNodeAsMap(dagId, true);
            nodes.values().forEach(n -> {
//                取消正在跑的其他任务
                if (Arrays.asList(NodeStatus.RUNNING, NodeStatus.PENDING).contains(n.getStatus())) {
                    for (DAGListener listener : getCombineListeners()) {
                        listener.onNodeCompleted(n, NodeStatus.CANCEL, null, cause);
                    }
                    repository.doInNewTransactional(() -> repository.updateNodeStatus(n.getId(), NodeStatus.CANCEL));
                }
            });
        }
    }

    public void finish() {
        if (isRunning.compareAndSet(true, false)) {
            Map<String, NodeDefinition> nodes = getNodeAsMap(dagId, false);
            boolean allSuccess = nodes.values().stream().allMatch(n -> n.getStatus() == NodeStatus.SUCCESS);
            DagStatus finalStatus = allSuccess ? DagStatus.SUCCESS : DagStatus.FAILED;

            for (DAGListener listener : getCombineListeners()) {
                listener.onCompleted(this, finalStatus, null);
            }
            repository.doInNewTransactional(() -> {
                repository.updateDagStatus(dagId, finalStatus);
            });
        }
    }

    public boolean isDone() {
        Map<String, NodeDefinition> nodes = getNodeAsMap(dagId, false);
        return nodes.values().stream().noneMatch(n -> Arrays.asList(NodeStatus.PENDING, NodeStatus.RUNNING).contains(n.getStatus()));
    }

    public void registerListener(DAGListener listener) {
        listeners.add(listener);
    }

    /**
     * 获取入度为0的节点
     *
     * @return
     */
    public Set<String> getReadNodes() {
        Set<String> readyNodes = new HashSet<>();
        Map<String, NodeDefinition> nodes = getNodeAsMap(dagId, false);
        Map<String, List<String>> dependencies = getEdgesAsMap();

        Map<String, Set<String>> predecessorMap = new HashMap<>();
        dependencies.forEach((key, set) -> set.forEach(item -> predecessorMap.computeIfAbsent(item, i -> new HashSet<>()).add(key)));

        for (Map.Entry<String, NodeDefinition> entry : nodes.entrySet()) {
            if (entry.getValue().getStatus().equals(NodeStatus.PENDING)) {
                String node = entry.getKey();
                Set<String> predecessorNodes = predecessorMap.getOrDefault(node, new HashSet<>());
                boolean allSuccess = predecessorNodes.stream().allMatch(n -> {
                    NodeDefinition pre = nodes.get(n);
                    return pre == null || pre.getStatus().equals(NodeStatus.SUCCESS);
                });
                if (allSuccess) {
                    readyNodes.add(node);
                }
            }
        }
        return readyNodes;
    }

    /**
     * 获取node节点的后继节点(这些节点的前置任务已经完成）
     *
     * @param node
     * @return
     */
    public Set<String> getReadySuccessors(String node) {
        Set<String> readyNodes = new HashSet<>();
        Map<String, NodeDefinition> nodes = getNodeAsMap(dagId, false);
        Map<String, List<String>> dependencies = getEdgesAsMap();

        Map<String, Set<String>> predecessorMap = new HashMap<>();
        dependencies.forEach((key, set) -> set.forEach(item -> predecessorMap.computeIfAbsent(item, i -> new HashSet<>()).add(key)));

        for (String successor : dependencies.getOrDefault(node, new ArrayList<>(0))) {
            NodeDefinition successorNode = nodes.get(successor);
            if (successorNode.getStatus().equals(NodeStatus.PENDING)) {
                Set<String> predecessorNodes = predecessorMap.getOrDefault(successor, new HashSet<>());
                boolean allSuccess = predecessorNodes.stream().allMatch(n -> {
                    NodeDefinition pre = nodes.get(n);
                    return pre == null || pre.getStatus().equals(NodeStatus.SUCCESS);
                });
                if (allSuccess) {
                    readyNodes.add(successor);
                }
            }
        }
        return readyNodes;
    }


    /**
     * 查找从 startId 到 endId 的路径
     *
     * @param startId 起始节点ID
     * @param endId   目标节点ID
     * @return 从 startId 到 endId 的路径列表，如果不存在路径则返回空列表
     */
    public List<String> findPath(String startId, String endId) {
        return findPath(getEdgesAsMap(), startId, endId);
    }

    private List<String> findPath(Map<String, List<String>> dependencies, String startId, String endId) {
        List<String> path = new ArrayList<>();
        if (startId.equals(endId)) {
            path.add(startId);
            return path;
        }
        // 使用深度优先搜索查找路径
        Set<String> visited = new HashSet<>();
        if (dfsFindPath(dependencies, startId, endId, visited, path)) {
            return path;
        }
        return new ArrayList<>();
    }

    /**
     * 深度优先搜索查找路径的辅助方法
     *
     * @param current 当前节点ID
     * @param target  目标节点ID
     * @param visited 已访问节点集合
     * @param path    当前路径
     * @return 是否找到路径
     */
    private boolean dfsFindPath(Map<String, List<String>> dependencies, String current, String target, Set<String> visited, List<String> path) {
        // 标记当前节点为已访问并加入路径
        visited.add(current);
        path.add(current);

        // 如果到达目标节点，返回true
        if (current.equals(target)) {
            return true;
        }

        // 获取当前节点的后继节点
        List<String> successors = dependencies.getOrDefault(current, new ArrayList<>());

        for (String successor : successors) {
            if (!visited.contains(successor)) {
                if (dfsFindPath(dependencies, successor, target, visited, path)) {
                    return true;
                }
            }
        }

        // 如果从当前节点无法到达目标节点，则回溯
        path.remove(path.size() - 1);
        return false;
    }


    protected List<DAGListener> getCombineListeners() {
        List<DAGListener> result = new ArrayList<>(listeners.size());
        result.addAll(listeners);
        return result;
    }

    protected List<NodeDefinition> getNodes(String dagId, boolean allFields) {
        return repository.getNodesByDagId(dagId, allFields);
    }


    protected List<EdgeDefinition> getEdges(String dagId) {
        return repository.getEdgesByDagId(dagId);
    }


    protected Map<String, NodeDefinition> getNodeAsMap(String dagId, boolean allFields) {
        List<NodeDefinition> definitions = getNodes(dagId, allFields);
        return CollectionUtil.toMap(definitions, new ConcurrentHashMap<>(), NodeDefinition::getId);
    }


    /**
     * 返回dag的边，key: 前继节点, value: 后继节点
     * 例如： A-> C, A-> B， 则 A-> [B,C]
     *
     * @return
     */
    protected Map<String, List<String>> getEdgesAsMap() {
        List<EdgeDefinition> edges = getEdges(dagId);

        Map<String, List<String>> dependencies = new ConcurrentHashMap<>();
        for (EdgeDefinition edge : edges) {
            String fromNodeId = edge.getFromNodeId();
            String toNodeId = edge.getToNodeId();

            if (revered) {
                String temp = fromNodeId;
                fromNodeId = toNodeId;
                toNodeId = temp;
            }
            dependencies.computeIfAbsent(fromNodeId, k -> new ArrayList<>()).add(toNodeId);
        }
        return dependencies;
    }
}