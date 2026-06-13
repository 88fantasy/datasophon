package com.datasophon.api.service.extrepo.ctx;

import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.vo.extrepo.DAGNode;
import com.datasophon.common.Constants;
import com.datasophon.common.model.DAG;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.model.extrepo.DeploySrvModel;
import com.datasophon.dao.model.extrepo.ServiceResource;

import org.apache.hadoop.util.VersionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Data;
import cn.hutool.core.util.StrUtil;

/**
 * @author zhanghuangbin
 */
@Data
public class ProductDeployDAGBuildContext {
    
    private final Map<String, List<FrameServiceEntity>> map;
    
    public ProductDeployDAGBuildContext(List<FrameServiceEntity> list) {
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
                        }));
        this.map.forEach((key, srvList) -> {
            srvList.sort((s1, s2) -> -VersionUtil.compareVersions(s1.getServiceVersion(), s2.getServiceVersion()));
        });
    }
    
    public FrameServiceEntity getSrvEntity(DeploySrvModel srv) {
        List<FrameServiceEntity> list = map.get(srv.getName());
        return list == null ? null : list.stream().filter(s -> s.getServiceVersion().equals(srv.getVersion())).findFirst().orElse(null);
    }
    
    public FrameServiceEntity getHighestVersionSrv(String srvName) {
        List<FrameServiceEntity> list = map.get(srvName);
        return list == null || list.isEmpty() ? null : list.get(0);
    }
    
    public <T extends ServiceResource<T>, N extends DAGNode> DAG<String, N, Integer> buildDeployDAG(List<T> serviceList,
                                                                                                    Function<T, N> nodeGenerator) {
        DAG<String, N, Integer> dag = new DAG<>();
        
        for (int i = 0; i < serviceList.size(); i++) {
            T srv = serviceList.get(i);
            N node = nodeGenerator.apply(srv);
            node.setId(i);
            node.setState(0);
            dag.addNode(srv.getName(), node);
        }
        
        addDirectEdge(dag, serviceList);
        return dag.getReverseDag();
    }
    
    /**
     * 直接加入依赖关系，不生成中间节点
     *
     */
    private void addDirectEdge(DAG<String, ?, Integer> dag, List<? extends ServiceResource> serviceList) {
        int edgeIdCounter = 0;
        
        for (int i = 0; i < serviceList.size(); i++) {
            ServiceResource start = serviceList.get(i);
            Set<String> dependencies = getDependencies(start.getName());
            
            for (int j = 0; j < serviceList.size(); j++) {
                // 不能自依赖
                if (i == j) {
                    continue;
                }
                ServiceResource<?> end = serviceList.get(j);
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
    
    private List<String> normalDependencies(String dependenciesStr) {
        List<String> dependencies = StrUtil.isEmpty(dependenciesStr) ? Collections.emptyList() : Arrays.asList(dependenciesStr.split(Constants.COMMA));
        return dependencies.stream().filter(StrUtil::isNotBlank).collect(Collectors.toList());
    }
    
}
