package com.datasophon.api.service.dag;

import com.datasophon.api.dag.model.DagDefinition;
import com.datasophon.api.dag.repo.DAGRepository;
import com.datasophon.dao.entity.dag.DagDefinitionEntity;
import com.datasophon.dao.entity.dag.EdgeDefinitionEntity;
import com.datasophon.dao.entity.dag.NodeDefinitionEntity;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * @author zhanghuangbin
 */
public interface DAGService extends DAGRepository {
    
    String saveDAG(Integer clusterId, DagDefinition definition);
    
    List<NodeDefinitionEntity> saveNodes(String dagId, List<NodeDefinitionEntity> nodes);
    
    EdgeDefinitionEntity saveEdge(String dagId, NodeDefinitionEntity start, NodeDefinitionEntity end);
    
    IPage<DagDefinitionEntity> findDagByPage(Integer clusterId, Integer page, Integer pageSize);
}
