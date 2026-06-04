package com.datasophon.common.k8s.dto;

import lombok.Data;

/**
 * Helm Uninstall 参数
 */
@Data
public class UninstallParams {
    /**
     * release 名称
     */
    private String releaseName;
    
    /**
     * 命名空间
     */
    private String namespace;
    
    /**
     * 超时时间（秒）
     */
    private int timeoutSeconds = 30;
    
    /**
     * 保留 release 历史记录
     */
    private boolean keepHistory = false;
    
}
