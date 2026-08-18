package com.datasophon.dao.vo.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sServiceInstanceVO {

    private Integer id;

    @Schema(description = "集群")
    private Integer clusterId;

    @Schema(description = "名空间ID")
    private Integer namespaceId;

    @Schema(description = "名空间")
    private String namespace;

    @Schema(description = "分类， ENVIRONMENT=基础环境, MIDDLEWARE=中间件, APPLICATION=应用")
    private String catalog;

    @Schema(description = "服务ID")
    private Integer serviceId;

    @Schema(description = "服务名称")
    private String serviceName;

    @Schema(description = "0初始化 1成功 2失败")
    private Integer state;

    @Schema(description = "来源：INSTALLED=平台安装, IMPORTED=接管登记")
    private String source;

    @Schema(description = "接管实例对应的 helm release 名，平台安装的为空")
    private String releaseName;

    @Schema(description = "指标 job（对应 Doris service_name），多个以英文逗号分隔")
    private String metricsJob;

    /** 非数据库列：轻对账发现 release 已从目标集群消失时为 true，仅接管实例会被赋值。 */
    @Schema(description = "接管实例失联标记：对应的 Helm release 已不在目标集群中")
    private Boolean missing;
}
