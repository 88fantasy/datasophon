package com.datasophon.api.service.dag.impl;

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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.hutool.core.bean.BeanUtil;

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
        DagDefinitionEntity dag = dagDefinitionEntityMapper.selectById(dagId);
        if (DagStatus.RUNNING.equals(status)) {
            if (dag.getStartedTime() == null) {
                dag.setStartedTime(LocalDateTime.now());
            }
            dag.setCompletedTime(null);
        }
        if (Arrays.asList(DagStatus.CANCEL, DagStatus.FAILED, DagStatus.SUCCESS).contains(status) && dag.getCompletedTime() == null) {
            dag.setCompletedTime(LocalDateTime.now());
        }
        dag.setStatus(status);
        return dagDefinitionEntityMapper.updateById(dag);
    }
    
    @Override
    public int markNodesPending(String dagId, boolean ignoreSuccess) {
        LambdaQueryWrapper<NodeDefinitionEntity> query = Wrappers.lambdaQuery(NodeDefinitionEntity.class)
                .eq(NodeDefinitionEntity::getDagId, dagId);
        
        List<NodeDefinitionEntity> nodes = nodeDefinitionEntityMapper.selectList(query);
        
        List<NodeDefinitionEntity> result = new ArrayList<>();
        for (NodeDefinitionEntity node : nodes) {
            boolean ignore = ignoreSuccess && NodeStatus.SUCCESS.equals(node.getStatus());
            if (!ignore) {
                result.add(node);
            }
        }
        for (NodeDefinitionEntity node : result) {
            node.setStatus(NodeStatus.PENDING);
            if (node.getStartedTime() == null) {
                node.setStartedTime(LocalDateTime.now());
            }
            nodeDefinitionEntityMapper.updateById(node);
        }
        
        return result.size();
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
                    NodeDefinitionEntity::getStatus);
        }
        return BeanUtil.copyToList(nodeDefinitionEntityMapper.selectList(query), NodeDefinition.class);
    }
    
    @Override
    public void updateNode(NodeDefinition node) {
        NodeDefinitionEntity db = nodeDefinitionEntityMapper.selectById(node.getId());
        BeanUtil.copyProperties(node, db);
        nodeDefinitionEntityMapper.updateById(db);
    }
    
    @Override
    public int updateNodeStatus(String nodeId, NodeStatus status) {
        NodeDefinitionEntity node = nodeDefinitionEntityMapper.selectById(nodeId);
        if (NodeStatus.RUNNING.equals(status) && node.getStartedTime() == null) {
            node.setStartedTime(LocalDateTime.now());
        }
        if (Arrays.asList(NodeStatus.CANCEL, NodeStatus.FAILED, NodeStatus.SUCCESS).contains(status) && node.getCompletedTime() == null) {
            node.setCompletedTime(LocalDateTime.now());
        }
        node.setStatus(status);
        return nodeDefinitionEntityMapper.updateById(node);
    }
    
    @Override
    public List<EdgeDefinition> getEdgesByDagId(String dagId) {
        List<EdgeDefinitionEntity> entities = edgeDefinitionEntityMapper.selectList(Wrappers.lambdaQuery(EdgeDefinitionEntity.class).eq(EdgeDefinitionEntity::getDagId, dagId));
        return BeanUtil.copyToList(entities, EdgeDefinition.class);
    }
    
    @Override
    public String saveDAG(Integer clusterId, DagDefinition definition) {
        DagDefinitionEntity dag = BeanUtil.toBean(definition, DagDefinitionEntity.class);
        dag.setClusterId(clusterId);
        dag.setCreatedTime(LocalDateTime.now());
        dag.setStatus(DagStatus.PENDING);
        dagDefinitionEntityMapper.insert(dag);
        return dag.getId();
    }
    
    @Override
    public List<NodeDefinitionEntity> saveNodes(String dagId, List<NodeDefinitionEntity> nodes) {
        nodes.forEach(node -> {
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
    public IPage<DagDefinitionEntity> findDagByPage(Integer clusterId, Integer page, Integer pageSize) {
        return dagDefinitionEntityMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.lambdaQuery(DagDefinitionEntity.class)
                        .eq(DagDefinitionEntity::getClusterId, clusterId)
                        .orderByDesc(DagDefinitionEntity::getCreatedTime));
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void doInNewTransactional(Runnable runnable) {
        DAGService.super.doInNewTransactional(runnable);
    }
}
