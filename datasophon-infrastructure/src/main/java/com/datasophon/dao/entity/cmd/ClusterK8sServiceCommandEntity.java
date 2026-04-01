package com.datasophon.dao.entity.cmd;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.jackson.annotation.WithEnumDescription;
import com.datasophon.common.jackson.annotation.WithEnumSourceDescription;
import com.datasophon.dao.enums.CommandState;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

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
    private String commandId;
    /**
     * 创建人
     */
    private String createBy;

    /**
     * 命令名称
     */
    private String commandName;
    /**
     * 命令状态 1：正在运行2：成功3：失败
     */
    @WithEnumDescription(fieldNameTpl = "#field + 'Code'", field = "value")
    private CommandState commandState;

    /**
     * 命令进度
     */
    private Integer commandProgress;

    /**
     * 集群id
     */
    private Integer clusterId;
    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 服务实例ID
     */
    private Integer serviceInstanceId;


    /**
     * 命令类型
     */
    @WithEnumSourceDescription(datasource = CommandType.class, valueMapping = "value", descMapping = "cnDesc")
    private Integer commandType;


    /**
     * 部署的名空间
     */
    private String namespace;

    /**
     * 创建时间
     */
    private Date createTime;

    private Date endTime;



    @TableField(exist = false)
    private String durationTime;
}
