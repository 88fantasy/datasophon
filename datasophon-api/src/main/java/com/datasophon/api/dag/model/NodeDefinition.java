package com.datasophon.api.dag.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.datasophon.dao.enums.dag.NodeStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

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
