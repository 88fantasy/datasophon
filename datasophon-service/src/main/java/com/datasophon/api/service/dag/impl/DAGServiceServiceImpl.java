package com.datasophon.api.service.dag.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.datasophon.api.dag.model.DagDefinition;
import com.datasophon.api.dag.model.EdgeDefinition;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.dao.entity.dag.DagDefinitionEntity;
import com.datasophon.dao.entity.dag.EdgeDefinitionEntity;
import com.datasophon.dao.entity.dag.NodeDefinitionEntity;
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.enums.dag.NodeStatus;
import com.datasophon.dao.mapper.dag.DagDefinitionEntityMapper;
import com.datasophon.dao.mapper.dag.EdgeDefinitionEntityMapper;
import com.datasophon.dao.mapper.dag.NodeDefinitionEntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author zhanghuangbin
 */
@Service("dagService")
public class DAGServiceServiceImpl implements DAGService {

    @Autowired
    private DagDefinitionEntityMapper dagDefinitionEntityMapper;

    @Autowired
    private NodeDefinitionEntityMapper nodeDefinitionEntityMapper;

    @Autowired
    private EdgeDefinitionEntityMapper edgeDefinitionEntityMapper;

    @Override
    public DagDefinition getDagById(String dagId) {
        return BeanUtil.toBean(dagDefinitionEntityMapper.selectById(dagId), DagDefinition.class);
    }

    @Override
    public int updateDagStatus(String dagId, DagStatus status) {
        return dagDefinitionEntityMapper.update(
                Wrappers.lambdaUpdate(DagDefinitionEntity.class)
                        .set(DagDefinitionEntity::getStatus, status)
                        .eq(DagDefinitionEntity::getId, dagId)
        );
    }

    @Override
    public int markNodesPending(String dagId, boolean ignoreSuccess) {
        LambdaUpdateWrapper<NodeDefinitionEntity> update = Wrappers.lambdaUpdate(NodeDefinitionEntity.class)
                .set(NodeDefinitionEntity::getStatus, NodeStatus.PENDING)
                .eq(NodeDefinitionEntity::getId, dagId);
        if (ignoreSuccess) {
            update = update.ne(NodeDefinitionEntity::getStatus, NodeStatus.SUCCESS);
        }
        return nodeDefinitionEntityMapper.update(update);
    }

    @Override
    public NodeDefinition getNodeById(String nodeId) {
        return BeanUtil.toBean(nodeDefinitionEntityMapper.selectById(nodeId), NodeDefinition.class);
    }

    @Override
    public List<NodeDefinition> getNodesByDagId(String dagId, boolean allFields) {
       LambdaQueryWrapper<NodeDefinitionEntity> query = Wrappers.lambdaQuery(NodeDefinitionEntity.class).eq(NodeDefinitionEntity::getDagId, dagId);
       if (!allFields) {
           query = query.select(
                   NodeDefinitionEntity::getId,
                   NodeDefinitionEntity::getDagId,
                   NodeDefinitionEntity::getNodeName,
                   NodeDefinitionEntity::getStatus
           );
       }
       return BeanUtil.copyToList(nodeDefinitionEntityMapper.selectList(query), NodeDefinition.class);
    }

    @Override
    public void updateNode(NodeDefinition node) {
        NodeDefinitionEntity db =  nodeDefinitionEntityMapper.selectById(node.getId());
        BeanUtil.copyProperties(node, db);
        nodeDefinitionEntityMapper.updateById(db);
    }

    @Override
    public int updateNodeStatus(String nodeId, NodeStatus status) {
        return nodeDefinitionEntityMapper.update(
                Wrappers.lambdaUpdate(NodeDefinitionEntity.class)
                        .set(NodeDefinitionEntity::getStatus, status)
                        .eq(NodeDefinitionEntity::getId, nodeId)
        );
    }

    @Override
    public List<EdgeDefinition> getEdgesByDagId(String dagId) {
        List<EdgeDefinitionEntity> entities = edgeDefinitionEntityMapper.selectList(Wrappers.lambdaQuery(EdgeDefinitionEntity.class).eq(EdgeDefinitionEntity::getDagId, dagId));
        return BeanUtil.copyToList(entities, EdgeDefinition.class);
    }

    @Override
    public String saveDAG(DagDefinition definition) {
        DagDefinitionEntity dag = BeanUtil.toBean(definition, DagDefinitionEntity.class);
        dag.setCreatedTime(LocalDateTime.now());
        dag.setStatus(DagStatus.PENDING);
        dagDefinitionEntityMapper.insert(dag);
        return dag.getId();
    }

    @Override
    public List<NodeDefinitionEntity> saveNodes(String dagId, List<NodeDefinitionEntity> nodes) {
        nodes.forEach(node-> {
            node.setDagId(dagId);
            node.setCreatedTime(LocalDateTime.now());
            node.setStatus(NodeStatus.PENDING);
        });
        nodeDefinitionEntityMapper.insert(nodes, 20);
        return nodes;
    }

    @Override
    public EdgeDefinitionEntity saveEdge(String dagId, NodeDefinitionEntity start, NodeDefinitionEntity end) {
        EdgeDefinitionEntity edge = new EdgeDefinitionEntity();
        edge.setFromNodeId(start.getId());
        edge.setToNodeId(end.getId());
        edge.setCreatedTime(LocalDateTime.now());
        edge.setDagId(dagId);
        edgeDefinitionEntityMapper.insert(edge);
        return edge;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void doInNewTransactional(Runnable runnable) {
        DAGService.super.doInNewTransactional(runnable);
    }
}
