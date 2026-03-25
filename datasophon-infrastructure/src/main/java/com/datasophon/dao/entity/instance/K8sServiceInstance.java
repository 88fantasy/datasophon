package com.datasophon.dao.entity.instance;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @author zhanghuangbin
 */
@Data
@TableName("t_ddh_k8s_service_instance")
public class K8sServiceInstance implements Serializable {

    @TableId
    private Integer id;

    @Schema(description = "集群")
    private Integer clusterId;

    @Schema(description = "名空间ID")
    private Integer namespaceId;

    @Schema(description = "服务ID")
    private Integer serviceId;

    @Schema(description = "管理状态 -1未知状态(即vos之前管理过这个数据，但是，后来账号没有了权限) 0初始化 1受控")
    private Integer state;
}
