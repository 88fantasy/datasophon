package com.datasophon.common.storage.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zhanghuangbin
 */
@Data
@NoArgsConstructor
public class DownloadResult {
    
    private boolean change;
    
    private String target;
    
    private String md5;
    
}
