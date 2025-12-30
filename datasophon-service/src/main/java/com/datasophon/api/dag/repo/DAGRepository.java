package com.datasophon.api.dag.repo;

import com.datasophon.api.dag.model.DagDefinition;
import com.datasophon.api.dag.model.EdgeDefinition;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.enums.dag.NodeStatus;

import java.util.List;

public interface DAGRepository {


    DagDefinition getDagById(String dagId);
    int updateDagStatus(String dagId, DagStatus status);
    int markNodesPending(String dagId, boolean ignoreSuccess);


    NodeDefinition getNodeById(String nodeId);
    List<NodeDefinition> getNodesByDagId(String dagId, boolean allFields);
    void updateNode(NodeDefinition node);
    int updateNodeStatus(String nodeId, NodeStatus nodeStatus);

    List<EdgeDefinition> getEdgesByDagId(String dagId);

    default void doInNewTransactional(Runnable runnable) {
        runnable.run();
    }

}