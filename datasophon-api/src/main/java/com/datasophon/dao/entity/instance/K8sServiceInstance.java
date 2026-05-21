package com.datasophon.dao.entity.instance;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 *
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

    @Schema(description = "0初始化 1成功 2失败")
    private Integer state;

    @Schema(description = "最近一次部署方式 helm, yaml")
    private String lastMetaFileType;

}
