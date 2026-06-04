package com.datasophon.api.dto.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableField;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sProductDeployMapping {
    
    @Schema(description = "服务名称")
    private String serviceName;
    
    @Schema(description = "安装的名空间")
    private String namespace;
    
    @Schema(description = "部署清单采用的部署方式")
    @TableField(exist = false)
    private String metaFileType;
    
}
