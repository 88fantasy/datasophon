package com.datasophon.dao.entity.k8s;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datasophon.dao.typehandler.ListStringHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author zhanghuangbin
 */

@Data
@TableName(value = "t_ddh_frame_k8s_service", autoResultMap = true)
public class FrameK8sServiceEntity implements Serializable {

    @TableId
    private Integer id;

    @Schema(description = "框架ID")
    private Integer frameId;

    @Schema(description = "服务名字")
    private String serviceName;

    @Schema(description = "版本")
    private String serviceVersion;

    @Schema(description = "描述")
    private String serviceDesc;

    @Schema(description = "依赖")
    @TableField(typeHandler = ListStringHandler.class)
    private List<String> dependencies;

    @Schema(description = "制品信息")
    private String artifact;

    @Schema(description = "支持的制品类型")
    @TableField(typeHandler = ListStringHandler.class)
    private List<String> supportArtifacts;

    @Schema(description = "定义的内容")
    private String manifestJson;


}
