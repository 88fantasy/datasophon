package com.datasophon.dao.entity.dag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_ddh_edge_definition_entity")
public class EdgeDefinitionEntity implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
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