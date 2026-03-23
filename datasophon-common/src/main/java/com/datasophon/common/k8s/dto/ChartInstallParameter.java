package com.datasophon.common.k8s.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 */
@Builder
@Data
public class ChartInstallParameter {

    /**
     * release 名称
     */
    private String releaseName;

    /**
     * chart名字
     */
    private String chart;
    /**
     * 命名空间
     */
    private String namespace;

    /**
     *可选的 values 文件列表
     */
    private List<String> valuesFiles;

    /**
     * 可选的 --set 参数列表（格式 "key=value"）
     */
    private List<String> setValues;

    private List<String> extraParameters;
}
