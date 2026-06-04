package com.datasophon.api.dag;

import com.datasophon.api.dag.model.NodeDefinition;

public interface NodeTask {
    /**
     * 异步执行节点
     * @param node 节点信息
     */
    String exec(NodeDefinition node) throws Exception;
    
}
