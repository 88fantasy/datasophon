package com.datasophon.dao.entity.cluster;

import com.datasophon.common.k8s.config.K8sClientConfig;
import com.datasophon.dao.enums.k8s.K8sAuthType;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@TableName("t_ddh_k8s_cluster_config")
public class K8sClusterConfig implements Serializable, K8sClientConfig {
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

    /**
     * updateStrategy=IGNORED：切换认证方式（type）时需要把非当前方式的凭据字段清空写库，
     * 全局 field-strategy 是 NOT_NULL，null 值默认不会进 UPDATE SET，这里按字段单独放开，
     * 避免切到 token/password 后旧的 kubeConfig 仍残留在库里被误用（见 SecureKubeConfigWriter）。
     */
    @Schema(description = "serviceAccount的token, type=token有效")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String token;

    @Schema(description = "用户名, type=password有效")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String username;

    @Schema(description = "密码, type=password有效")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String password;

    @Schema(description = "配置文件内容")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String kubeConfig;

    /**
     * 接管集群的 OTel 数据源。密码不落此表，走 {@code OtelCredentialService}。
     */
    @Schema(description = "接管集群 OTel Doris FE 主机")
    private String dorisHost;

    @Schema(description = "Doris MySQL 协议端口，缺省 9030")
    private Integer dorisPort;

    @Override
    public String getAuthType() {
        return type == null ? null : type.name();
    }
}
