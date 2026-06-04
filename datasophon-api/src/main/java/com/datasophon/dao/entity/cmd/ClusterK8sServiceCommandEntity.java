package com.datasophon.dao.entity.cmd;

import com.datasophon.common.enums.CommandType;
import com.datasophon.common.jackson.annotation.WithEnumDescription;
import com.datasophon.common.jackson.annotation.WithEnumSourceDescription;
import com.datasophon.dao.enums.CommandState;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * @author zhanghuangbin
 */
@TableName("t_ddh_cluster_k8s_service_command")
@Data
public class ClusterK8sServiceCommandEntity implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键
     */
    @TableId
    @Schema(description = "主键")
    private String commandId;
    /**
     * 创建人
     */
    @Schema(description = "创建人")
    private String createBy;
    
    /**
     * 命令名称
     */
    @Schema(description = "命令名称")
    private String commandName;
    /**
     * 命令状态 1：正在运行2：成功3：失败
     */
    @Schema(description = "命令状态（1：正在运行 2：成功 3：失败）")
    @WithEnumDescription(fieldNameTpl = "#field + 'Code'", field = "value")
    private CommandState commandState;
    
    /**
     * 命令进度
     */
    @Schema(description = "命令进度")
    private Integer commandProgress;
    
    /**
     * 集群id
     */
    @Schema(description = "集群 ID")
    private Integer clusterId;
    /**
     * 服务名称
     */
    @Schema(description = "服务名称")
    private String serviceName;
    
    /**
     * 服务实例ID
     */
    @Schema(description = "服务实例 ID")
    private Integer serviceInstanceId;
    
    /**
     * 命令类型
     */
    @Schema(description = "命令类型")
    @WithEnumSourceDescription(datasource = CommandType.class, valueMapping = "value", descMapping = "cnDesc")
    private Integer commandType;
    
    /**
     * 部署的名空间
     */
    @Schema(description = "命名空间")
    private String namespace;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "结束时间")
    private Date endTime;
    
    @Schema(description = "持续时间")
    public String getDurationTime() {
        if (createTime == null || endTime == null) {
            return null;
        }
        long diff = endTime.getTime() - createTime.getTime();
        long seconds = diff / 1000;
        return seconds + "秒";
    }
}
