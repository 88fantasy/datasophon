package com.datasophon.dao.entity.dag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datasophon.dao.enums.dag.NodeStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

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
