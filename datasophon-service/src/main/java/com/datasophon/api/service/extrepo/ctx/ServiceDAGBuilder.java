package com.datasophon.api.service.extrepo.ctx;

import cn.hutool.core.util.StrUtil;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.common.model.DAG;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author zhanghuangbin
 */
public class ServiceDAGBuilder<N extends ServiceDAGBuilder.Node> {



    public DAG<String, N, Integer> buildDeployDAG(List<N> serviceList) {

        DAG<String, N, Integer> dag = new DAG<>();

        for (N node : serviceList) {
            dag.addNode(node.getNodeName(), node);
        }

        addDirectEdge(dag, serviceList);
        return dag.getReverseDag();
    }


    /**
     * 直接加入依赖关系，不生成中间节点
     *
     * @param dag
     * @param serviceList
     */
    private void addDirectEdge(DAG<String, N, Integer> dag, List<N> serviceList) {
        int edgeIdCounter = 0;

        for (int i = 0; i < serviceList.size(); i++) {
            N start = serviceList.get(i);
            Set<String> dependencies = start.getDependencies();

            for (int j = 0; j < serviceList.size(); j++) {
//                不能自依赖
                if (i == j) {
                    continue;
                }
                N end = serviceList.get(j);
                if (dependencies.contains(end.getNodeName())) {
                    List<String> path = dag.findPath(end.getNodeName(), start.getNodeName());
                    if (path == null) {
                        dag.addEdge(start.getNodeName(), end.getNodeName(), edgeIdCounter++, false);
                    } else {
                        path = new ArrayList<>(path);
                        path.add(end.getNodeName());
                        throw new BusinessException(String.format("待安装的服务存在循环依赖, 依赖关系链为%s", StrUtil.join("->", path)));
                    }
                }
            }
        }
    }


    public interface Node {

        String getNodeName();

        Set<String> getDependencies();
    }
}
