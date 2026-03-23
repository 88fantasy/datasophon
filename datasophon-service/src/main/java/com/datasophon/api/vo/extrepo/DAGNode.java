package com.datasophon.api.vo.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class DAGNode {

    @Schema(description = "节点ID")
    private Integer id;

    @Schema(description = "服务名称")
    private String name;

    @Schema(description = "服务版本")
    private String version;

    @Schema(description = "服务状态 0表示需要安装 1表示就绪 2表示依赖软件未安装")
    private Integer state;
}
