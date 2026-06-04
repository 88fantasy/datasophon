package com.datasophon.common.utils.nexus.dto;

import lombok.Builder;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
@Builder
public class AssertQueryDTO {
    
    private String format;
    
    private String group;
    
    private String name;
    
}
