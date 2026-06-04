package com.datasophon.dao.entity.dag;

import com.datasophon.dao.enums.dag.NodeStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@TableName("t_ddh_node_definition_entity")
public class NodeDefinitionEntity implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String dagId;
    private String nodeName;
    private String nodeConfig;
    private NodeStatus status;
    private Integer timeoutSeconds;
    private LocalDateTime createdTime;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private String executionLog;
    
}
