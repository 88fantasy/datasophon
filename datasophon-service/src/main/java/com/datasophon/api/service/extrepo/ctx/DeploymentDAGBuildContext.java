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
import java.util.List;
import java.util.Map;
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


    public FrameServiceEntity getHighestVersionSrv(String srvName) {
        List<FrameServiceEntity> list = map.get(srvName);
        return list == null || list.isEmpty() ? null : list.get(0);
    }


    public DeploymentDAG buildDAG(List<DeploySrvModel> serviceList) {
        DAG<String, DeploymentDAG.SrvNodeVO, Integer> dag = new DAG<>();

        int nodeIdCounter = 0;
        int edgeIdCounter = 0;
        for (DeploySrvModel srv : serviceList) {
            DeploymentDAG.SrvNodeVO node = BeanUtil.toBean(srv, DeploymentDAG.SrvNodeVO.class);
            node.setId(nodeIdCounter++);
            node.setState(0);
            dag.addNode(srv.getName(), node);
        }

        Deque<String> queue = serviceList.stream().map(DeploySrvModel::getName).collect(Collectors.toCollection(ArrayDeque::new));
        while (!queue.isEmpty()) {
            String start = queue.poll();
            FrameServiceEntity entity = getHighestVersionSrv(start);
            List<String> dependencies = StrUtil.isEmpty(entity.getDependencies()) ? Collections.emptyList() : Arrays.asList(entity.getDependencies().split(Constants.COMMA));
            for (String dependency : dependencies) {
                DeploymentDAG.SrvNodeVO end = dag.getNode(dependency);
                if (end == null) {
                    FrameServiceEntity srvEnd = getHighestVersionSrv(dependency);
                    end = new DeploymentDAG.SrvNodeVO();
                    end.setName(srvEnd.getServiceName());
                    end.setVersion(srvEnd.getServiceVersion());
                    end.setId(nodeIdCounter++);
//                    FIXME pms保证需要增量更新的软件都存在
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


        DeploymentDAG result = new DeploymentDAG();
        dag.getNodes().values().forEach(node-> result.getNodes().add(node));
        dag.getEdges().forEach(edge-> {
            DeploymentDAG.EdgeVO vo = new DeploymentDAG.EdgeVO();
            vo.setId(edge.getEdge());
            vo.setStart(dag.getNode(edge.getStart()).getId());
            vo.setEnd(dag.getNode(edge.getEnd()).getId());
            result.getEdge().add(vo);
        });
        return result;
    }


}
