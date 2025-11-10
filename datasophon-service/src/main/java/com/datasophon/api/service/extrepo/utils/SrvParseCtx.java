package com.datasophon.api.service.extrepo.utils;

import lombok.Data;

/**
 * @author zhanghuangbin
 * @date 2025/11/10
 */
@Data
class SrvParseCtx {

    private String root;

    private String framework;


    public SrvParseCtx(String root, String framework) {
        this.root = root;
        this.framework = framework;
    }
}
