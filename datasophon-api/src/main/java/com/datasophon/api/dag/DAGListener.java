package com.datasophon.api.dag;

import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.enums.dag.NodeStatus;

public interface DAGListener {
    
    default void onStart(RepoDAG dag) {
    }
    
    default void onNodeStarted(NodeDefinition node) {
    }
    
    default void onNodeCompleted(NodeDefinition node, NodeStatus status, String result, Throwable throwable) {
        if (NodeStatus.SUCCESS.equals(status)) {
            onNodeSuccess(node, result);
        }
        if (NodeStatus.FAILED.equals(status)) {
            onNodeFail(node, throwable);
        }
        if (NodeStatus.CANCEL.equals(status)) {
            onNodeCancel(node, throwable);
        }
    }
    
    default void onNodeSuccess(NodeDefinition node, String result) {
    }
    
    default void onNodeFail(NodeDefinition node, Throwable throwable) {
    }
    
    default void onNodeCancel(NodeDefinition node, Throwable throwable) {
    }
    
    default void onCompleted(RepoDAG dag, DagStatus status, Throwable throwable) {
    }
}