package com.datasophon.common.k8s.vo.helm;

import com.datasophon.common.k8s.vo.k8s.K8sResource;

import java.util.List;
import java.util.Map;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Helm Status 命令返回的 VO 对象
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelmStatusVO {
    
    /**
     * release 名称
     */
    private String name;
    
    /**
     * 命名空间
     */
    private String namespace;
    
    /**
     * 修订版本号
     */
    private Integer version;
    
    /**
     * 信息详情
     */
    private Info info;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {
        /**
         * 首次部署时间
         */
        @JsonProperty("first_deployed")
        private String firstDeployed;
        
        /**
         * 最后部署时间
         */
        @JsonProperty("last_deployed")
        private String lastDeployed;
        
        /**
         * 描述信息
         */
        private String description;
        
        /**
         * 状态
         */
        private String status;
        
        /**
         * 注释
         */
        private String notes;
        
        private Map<String, List<K8sResource>> resources;
        
    }
    
}
