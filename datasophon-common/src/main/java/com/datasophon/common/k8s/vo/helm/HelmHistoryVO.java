package com.datasophon.common.k8s.vo.helm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Helm History VO - 用于表示 helm history 命令的返回结果
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelmHistoryVO {

    /**
     * 版本号
     */
    private Integer revision;

    /**
     * 安装时间
     */
    private String updated;

    /**
     * 状态
     */
    private String status;

    /**
     * 图表版本
     */
    private String chart;

    /**
     * 应用版本
     */
    private String appVersion;

    /**
     * 描述信息
     */
    private String description;
}
