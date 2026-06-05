package com.datasophon.api.service.extrepo.ctx;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class SrvParseCtx {
    
    private MetaParseOption option;
    
    private String framework;
    
    private List<String> errors;
    
    public SrvParseCtx(MetaParseOption option, String framework, List<String> errors) {
        this.option = option;
        this.framework = framework;
        this.errors = errors;
    }
    
    public void addError(String error) {
        errors.add(error);
    }
}
