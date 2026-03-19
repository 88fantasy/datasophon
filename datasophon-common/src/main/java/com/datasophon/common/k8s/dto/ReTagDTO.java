package com.datasophon.common.k8s.dto;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class ReTagDTO {

    private String oldTag;

    /**
     * 仓库地址前缀
     */
    private String repository;

    /**
     * 可选格式：
     * app:version
     */
    private String newTag;

    private boolean removeOldTag;
}
