package com.datasophon.api.vo.k8s;

import lombok.Data;

import java.util.Map;

/**
 * K8s 资源信息
 *
 * @author zhanghuangbin
 */
@Data
public class K8sResource {

    /**
     * 资源类型 (如：pod, service, deployment, statefulset 等)
     */
    private String kind;

    /**
     * 资源名称
     */
    private String name;

    /**
     * 所属命名空间
     */
    private String namespace;

    /**
     * 资源状态
     */
    private String status;

    /**
     * 副本数 (如：1/1)
     */
    private String replicas;

    /**
     * 创建时间
     */
    private String age;

    /**
     * 镜像信息
     */
    private String image;

    /**
     * 标签
     */
    private Map<String, String> labels;
}
