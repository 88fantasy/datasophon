package com.datasophon.api.vo.k8s;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sClusterStatus {


    private String k8sVersion;

    private List<NodeInfo> nodes = new ArrayList<>(0);

    private List<String> namespace = new ArrayList<>(0);



    @Data
    public static class NodeInfo {

        private String name;

        private String status;

    }


}
