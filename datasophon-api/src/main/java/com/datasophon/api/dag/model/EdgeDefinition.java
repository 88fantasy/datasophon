package com.datasophon.api.dag.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class EdgeDefinition implements Serializable {
    private String id;
    private String dagId;
    
    /**
     * A依赖于B，
     * 则 fromNodeId: A toNodeId:B
     * 依赖项
     */
    private String fromNodeId;
    
    /**
     * 被依赖
     */
    private String toNodeId;
    private LocalDateTime createdTime;
}