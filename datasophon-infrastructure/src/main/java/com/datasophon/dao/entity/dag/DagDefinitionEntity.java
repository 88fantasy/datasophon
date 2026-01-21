package com.datasophon.dao.entity.dag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datasophon.dao.enums.dag.DagStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_ddh_dag_definition_entity")
public class DagDefinitionEntity implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private Integer clusterId;

    private String dagName;
    private String description;
    private DagStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;

    

}

