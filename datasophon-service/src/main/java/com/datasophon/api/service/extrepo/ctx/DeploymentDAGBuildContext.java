package com.datasophon.api.service.extrepo.ctx;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.vo.extrepo.DeploymentDAG;
import com.datasophon.common.Constants;
import com.datasophon.common.model.DAG;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.model.extrepo.DeploySrvModel;
import lombok.Data;
import org.apache.hadoop.util.VersionUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
public class DeploymentDAGBuildContext {

    private final ClusterInfoEntity cluster;

    private final Map<String, List<FrameServiceEntity>> map;




    public DeploymentDAGBuildContext(ClusterInfoEntity cluster, List<FrameServiceEntity> list) {
        this.cluster = cluster;
        this.map = list.stream().collect(
                Collectors.toMap(
                        FrameServiceEntity::getServiceName,
                        a -> {
                            List<FrameServiceEntity> rs = new ArrayList<>();
                            rs.add(a);
                            return rs;
                        },
                        (a, b) -> {
                            a.addAll(b);
                            return b;
                        }
                )
        );
        this.map.forEach((key, srvList) -> {
            srvList.sort((s1, s2) -> -VersionUtil.compareVersions(s1.getServiceVersion(), s2.getServiceVersion()));
        });
    }


    public FrameServiceEntity getSrvEntity(DeploySrvModel srv) {
        List<FrameServiceEntity> list = map.get(srv.getName());
        return list == null ? null : list.stream().filter(s -> s.getServiceVersion().equals(srv.getVersion())).findFirst().orElse(null);
    }

    public FrameServiceEntity getSrvEntity(String srvName, String version) {
        List<FrameServiceEntity> list = map.get(srvName);
        return list == null ? null : list.stream().filter(s -> s.getServiceVersion().equals(version)).findFirst().orElse(null);
    }


    public FrameServiceEntity getHighestVersionSrv(String srvName) {
        List<FrameServiceEntity> list = map.get(srvName);
        return list == null || list.isEmpty() ? null : list.get(0);
    }


    /**
     * 构建部署依赖关系图。算法：
     * 1. 添加
     *
     * @param serviceList
     * @param includeVirtualNode 是否包含不在部署列表中的节点
     * @return
     */
    public DeploymentDAG buildDAG(List<DeploySrvModel> serviceList, boolean includeVirtualNode) {
        DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag = buildDeployDAG(serviceList, includeVirtualNode);
        DeploymentDAG result = new DeploymentDAG();
        dag.getNodes().values().forEach(node -> result.getNodes().add(node));
        dag.getEdges().forEach(edge -> {
            DeploymentDAG.EdgeVO vo = new DeploymentDAG.EdgeVO();
            vo.setId(edge.getEdge());
            vo.setStart(dag.getNode(edge.getStart()).getId());
            vo.setEnd(dag.getNode(edge.getEnd()).getId());
            result.getEdge().add(vo);
        });
        return result;
    }

    public DAG<String, DeploymentDAG.SrvNodeVO, Integer> buildDeployDAG(List<DeploySrvModel> serviceList, boolean includeVirtualNode) {
        DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag = new DAG<>();

        for (int i = 0; i < serviceList.size(); i++) {
            DeploySrvModel srv = serviceList.get(i);
            DeploymentDAG.SrvNodeVO node = BeanUtil.toBean(srv, DeploymentDAG.SrvNodeVO.class);
            node.setId(i);
            node.setState(0);
            dag.addNode(srv.getName(), node);
        }

        if (includeVirtualNode) {
            addEdgeIncludeInnerNode(dag, serviceList);
        } else {
            addDirectEdge(dag, serviceList);
        }


        return dag.getReverseDag();
    }


    /**
     * 添加依赖关系（如果存在中间依赖，也加入图中）
     *
     * @param dag
     * @param serviceList
     */
    private void addEdgeIncludeInnerNode(DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag, List<DeploySrvModel> serviceList) {
        Deque<String> queue = serviceList.stream().map(DeploySrvModel::getName).collect(Collectors.toCollection(ArrayDeque::new));

        int edgeIdCounter = 0;
        int nodeIdCounter = dag.getNodesCount();
        while (!queue.isEmpty()) {
            String start = queue.poll();
            FrameServiceEntity entity = getHighestVersionSrv(start);
            List<String> dependencies = normalDependencies(entity.getDependencies());
            for (String dependency : dependencies) {
                DeploymentDAG.SrvNodeVO end = dag.getNode(dependency);
                if (end == null) {
                    FrameServiceEntity srvEnd = getHighestVersionSrv(dependency);
                    end = new DeploymentDAG.SrvNodeVO();
                    end.setName(srvEnd.getServiceName());
                    end.setVersion(srvEnd.getServiceVersion());
                    end.setId(nodeIdCounter++);
                    end.setState(srvEnd.getInstalled() ? 1 : 2);
                    end.setDesc(srvEnd.getServiceDesc());
                    dag.addNode(dependency, end);
                }
//                如果存在从dependency->start的路径，则添加start->dependency这条边，一定会形成环。
                List<String> path = dag.findPath(dependency, start);
                if (path == null) {
                    dag.addEdge(start, dependency, edgeIdCounter++, false);
                } else {
                    path = new ArrayList<>(path);
                    path.add(dependency);
                    throw new BusinessException(String.format("待安装的服务存在循环依赖, 依赖关系链为%s", StrUtil.join("->", path)));
                }
                queue.add(dependency);
            }
        }
    }

    private List<String> normalDependencies(String dependenciesStr) {
        List<String> dependencies = StrUtil.isEmpty(dependenciesStr) ? Collections.emptyList() : Arrays.asList(dependenciesStr.split(Constants.COMMA));
        return dependencies.stream().filter(StrUtil::isNotBlank).collect(Collectors.toList());
    }


    /**
     * 直接加入依赖关系，不生成中间节点
     *
     * @param dag
     * @param serviceList
     */
    private void addDirectEdge(DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag, List<DeploySrvModel> serviceList) {
        int edgeIdCounter = 0;

        for (int i = 0; i < serviceList.size(); i++) {
            DeploySrvModel start = serviceList.get(i);
            Set<String> dependencies = getDependencies(start.getName());

            for (int j = 0; j < serviceList.size(); j++) {
//                不能自依赖
                if (i == j) {
                    continue;
                }
                DeploySrvModel end = serviceList.get(j);
                if (dependencies.contains(end.getName())) {
                    List<String> path = dag.findPath(end.getName(), start.getName());
                    if (path == null) {
                        dag.addEdge(start.getName(), end.getName(), edgeIdCounter++, false);
                    } else {
                        path = new ArrayList<>(path);
                        path.add(end.getName());
                        throw new BusinessException(String.format("待安装的服务存在循环依赖, 依赖关系链为%s", StrUtil.join("->", path)));
                    }
                }
            }
        }
    }

    public Set<String> getDependencies(String srv) {
        Set<String> visited = new HashSet<>();
        doAddDependencies(visited, srv);
        return visited;
    }

    private void doAddDependencies(Set<String> visited, String name) {
        FrameServiceEntity entity = getHighestVersionSrv(name);
        if (entity == null) {
            return;
        }
        List<String> dependencies = normalDependencies(entity.getDependencies());
        for (String dependency : dependencies) {
            if (!visited.contains(dependency)) {
                visited.add(dependency);
                doAddDependencies(visited, dependency);
            }
        }
    }



}
