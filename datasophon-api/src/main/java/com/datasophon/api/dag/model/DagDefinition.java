package com.datasophon.api.dag.model;

import com.datasophon.dao.enums.dag.DagStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DagDefinition implements Serializable {
    private String id;
    private DagStatus status;
    
    private String dagName;
    private String description;
    private LocalDateTime createdTime;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    
}
