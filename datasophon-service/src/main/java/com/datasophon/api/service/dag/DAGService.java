package com.datasophon.api.service.dag;

import com.datasophon.api.dag.model.DagDefinition;
import com.datasophon.api.dag.repo.DAGRepository;
import com.datasophon.dao.entity.dag.EdgeDefinitionEntity;
import com.datasophon.dao.entity.dag.NodeDefinitionEntity;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface DAGService extends DAGRepository {

    String saveDAG(DagDefinition definition);

    List<NodeDefinitionEntity> saveNodes(String dagId, List<NodeDefinitionEntity> nodes);

    EdgeDefinitionEntity saveEdge(String dagId, NodeDefinitionEntity start, NodeDefinitionEntity end);
}
