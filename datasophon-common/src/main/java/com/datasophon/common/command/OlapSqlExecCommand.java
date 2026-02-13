package com.datasophon.common.command;

import java.io.Serializable;

import lombok.Data;

@Data
public class OlapSqlExecCommand implements Serializable {

    private static final long serialVersionUID = -3885610955649809446L;

    private Integer clusterId;
    
    private OlapOpsType opsType;
    
    private String feMaster;
    
    private String hostName;

    private String workerPath;

}
