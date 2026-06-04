package com.datasophon.common.k8s.vo.k8s;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * K8s 资源列表响应
 */
@Data
public class K8sResourceList<T> {
    private String apiVersion;
    private String kind;
    private Map<String, String> metadata;
    private List<T> items;
}
