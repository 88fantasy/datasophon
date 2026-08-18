package com.datasophon.dao.entity.instance;

import com.datasophon.dao.enums.k8s.InstanceSource;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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

    @Schema(description = "来源 INSTALLED=平台安装 IMPORTED=扫描接管")
    private InstanceSource source;

    @Schema(description = "接管实例对应的 Helm release 名")
    private String releaseName;

    /**
     * 逗号分隔的多值：一个服务可能对应多个 Prometheus job，
     * 例如 DolphinScheduler 有 api / master-headless / worker-headless 三个。
     */
    @Schema(description = "OTel service_name(job) 列表，逗号分隔")
    private String metricsJob;

}
