package com.datasophon.api.dag.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

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