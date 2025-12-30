package com.datasophon.api.service.dag;

import com.datasophon.api.dag.repo.DAGRepository;
import com.datasophon.dao.entity.dag.EdgeDefinitionEntity;
import com.datasophon.dao.entity.dag.NodeDefinitionEntity;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface DAGService extends DAGRepository {

    String saveDAG(String dagName, String desc);

    List<NodeDefinitionEntity> saveNodes(String dagId, List<NodeDefinitionEntity> nodes);

    EdgeDefinitionEntity saveEdge(String dagId, NodeDefinitionEntity start, NodeDefinitionEntity end);
}
