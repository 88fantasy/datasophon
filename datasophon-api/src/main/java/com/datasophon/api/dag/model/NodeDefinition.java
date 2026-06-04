package com.datasophon.api.dag.model;

import com.datasophon.dao.enums.dag.NodeStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

@Data
public class NodeDefinition implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String dagId;
    private String nodeName;
    private Object nodeConfig;
    private NodeStatus status;
    private Integer timeoutSeconds;
    private LocalDateTime createdTime;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private String executionLog;
    
}
