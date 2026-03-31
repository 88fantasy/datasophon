package com.datasophon.common.k8s.vo.helm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Helm Release VO - 用于表示 Helm upgrade 等操作的返回结果
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelmReleaseVO {

    /**
     * Release 名称
     */
    private String name;

    /**
     * Release 信息
     */
    private Info info;


    /**
     * 版本号
     */
    private Integer version;

    /**
     * 命名空间
     */
    private String namespace;



    /**
     * Release 信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {
        /**
         * 首次部署时间
         */
        private String firstDeployed;

        /**
         * 最后部署时间
         */
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
         * 说明备注
         */
        private String notes;
    }


}
