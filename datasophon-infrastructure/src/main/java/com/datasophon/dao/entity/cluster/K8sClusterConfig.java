package com.datasophon.dao.entity.cluster;


import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datasophon.dao.enums.k8s.K8sAuthType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@TableName("t_ddh_k8s_cluster_config")
public class K8sClusterConfig implements Serializable {
    @TableId
    @Schema(description = "id")
    private Integer id;

    @Schema(description = "集群id")
    @NotNull(message = "集群id不能为空")
    private Integer clusterId;

    @Schema(description = "连接集群方式, config_file: config配置文件, token:使用token方式, password:使用用户名/密码登录")
    @NotNull(message = "连接集群方式不能为空")
    private K8sAuthType type;

    @Schema(description = "k8s主机名称，type=token/password有效")
    private String serverHost;

    @Schema(description = "k8s证书, type=token/password有效")
    private String serverCert;

    @Schema(description = "serviceAccount的token, type=token有效")
    private String token;

    @Schema(description = "用户名, type=password有效")
    private String username;

    @Schema(description = "密码, type=password有效")
    private String password;

    @Schema(description = "配置文件内容")
    private String kubeConfig;
}
