package com.datasophon.common.command.dag;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class DAGExecCommand {
    
    private String dagId;
    
    private boolean restart;
    
}
