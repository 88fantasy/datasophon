package com.datasophon.common.k8s.dto;

import java.util.List;

import lombok.Data;

/**
 * Helm Upgrade 参数构建器
 */
@Data
public class UpgradeParams {
    /**
     * release 名称
     */
    private String releaseName;
    
    /**
     * Chart 路径（本地路径或 chart 引用）
     */
    private String chartPath;
    
    /**
     * values 文件列表
     */
    private List<String> valuesFiles;
    
    /**
     * set 参数列表（key=value 格式）
     */
    private List<String> setValues;
    
    /**
     * set-file 参数列表（key=filepath 格式，从文件读取值）
     */
    private List<String> setFileValues;
    
    /**
     * 命名空间
     */
    private String namespace;
    
    /**
     * 超时时间（秒）
     */
    private int timeoutSeconds = 300;
    
    /**
     * 如果 release 不存在则安装
     */
    private boolean install = true;
    
    /**
     * 等待资源就绪
     */
    private boolean wait = true;
    
    private boolean waitForJob = false;
    
    /**
     * 自定义描述
     */
    private String description;
    
}
