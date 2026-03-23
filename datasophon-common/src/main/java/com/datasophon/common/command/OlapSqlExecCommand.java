package com.datasophon.common.command;

import java.io.Serializable;
import java.util.Map;

import lombok.Data;

@Data
public class OlapSqlExecCommand implements Serializable {

    private static final long serialVersionUID = -3885610955649809446L;
    
    private OlapOpsType opsType;
    
    private String feMaster;
    
    private String hostName;

    private String workerPath;

    private Map<String,String> variables;

}
