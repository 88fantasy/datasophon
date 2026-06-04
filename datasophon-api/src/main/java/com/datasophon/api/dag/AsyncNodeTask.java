package com.datasophon.api.dag;

import com.datasophon.api.dag.model.NodeDefinition;

public interface AsyncNodeTask {
    /**
     * 异步执行节点
     * @param node 节点信息
     * @param callback 执行完成后的回调
     */
    void exec(NodeDefinition node, NodeExecutionCallback callback);
    
}
