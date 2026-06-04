package com.datasophon.api.vo.k8s;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

import lombok.Data;

/**
 * K8s ConfigMap 资源信息
 *
 * @author zhanghuangbin
 */
@Data
public class K8sConfigMapInfo {
    
    @Schema(description = "ConfigMap 名称")
    private String name;
    
    @Schema(description = "命名空间")
    private String namespace;
    
    @Schema(description = "运行时间")
    private String age;
    
    @Schema(description = "标签")
    private Map<String, String> labels;
    
}
