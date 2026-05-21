package com.datasophon.api.dag.repo;

import cn.hutool.core.bean.BeanUtil;
import com.datasophon.api.dag.model.DagDefinition;
import com.datasophon.api.dag.model.EdgeDefinition;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.enums.dag.NodeStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhanghuangbin
 */
public class SimpleDAGRepository implements DAGRepository{


    private final DagDefinition dag;

    private final List<NodeDefinition> nodes;

    private final List<EdgeDefinition> edges;

    public static final String DAG_ID = "1";

    public SimpleDAGRepository(List<Object> configs, List<int[]> edges) {
       dag = new DagDefinition();
       dag.setStatus(DagStatus.PENDING);
       dag.setCreatedTime(LocalDateTime.now());
       dag.setId(DAG_ID);

        nodes = new ArrayList<>(configs.size());
        for (int i = 0; i < configs.size(); i++) {
            NodeDefinition node = new NodeDefinition();
            node.setId(i + "");
            node.setNodeName("task" + i);
            node.setCreatedTime(LocalDateTime.now());
            node.setStatus(NodeStatus.PENDING);
            node.setNodeConfig(configs.get(i));
            node.setDagId(DAG_ID);
            nodes.add(node);
        }

        this.edges = new ArrayList<>();
        for (int[] edge : edges) {
            EdgeDefinition ed = new EdgeDefinition();
            ed.setCreatedTime(LocalDateTime.now());
            ed.setFromNodeId(edge[0] + "");
            ed.setToNodeId(edge[1] + "");
            ed.setDagId(DAG_ID);
            this.edges.add(ed);
        }

    }

    @Override
    public DagDefinition getDagById(String dagId) {
        return dag;
    }

    @Override
    public int updateDagStatus(String dagId, DagStatus status) {
        dag.setStatus(status);
        return 1;
    }

    @Override
    public int markNodesPending(String dagId, boolean ignoreSuccess) {
        nodes.forEach(n-> {
            if(n.getStatus().equals(NodeStatus.SUCCESS) && ignoreSuccess) {
                return;
            }
            n.setStatus(NodeStatus.PENDING);
        });
        return nodes.size();
    }

    @Override
    public NodeDefinition getNodeById(String nodeId) {
        return nodes.stream().filter(n-> n.getId().equals(nodeId)).findFirst().get();
    }

    @Override
    public List<NodeDefinition> getNodesByDagId(String dagId, boolean allFields) {
        return nodes;
    }

    @Override
    public void updateNode(NodeDefinition node) {
        NodeDefinition target = getNodeById(node.getId());
        BeanUtil.copyProperties(node, target);

    }

    @Override
    public int updateNodeStatus(String nodeId, NodeStatus nodeStatus) {
        NodeDefinition target = getNodeById(nodeId);
        target.setStatus(nodeStatus);
        return 1;
    }

    @Override
    public List<EdgeDefinition> getEdgesByDagId(String dagId) {
        return edges;
    }

}
