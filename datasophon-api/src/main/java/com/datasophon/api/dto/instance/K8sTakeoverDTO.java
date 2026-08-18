package com.datasophon.api.dto.instance;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 接管流程的请求体。 */
public class K8sTakeoverDTO {

    /** 保存接管集群的 OTel Doris 数据源。 */
    @Data
    public static class DatasourceSave {

        @Schema(description = "Doris FE 主机，需平台可直连")
        @NotBlank(message = "Doris 主机不能为空")
        private String host;

        @Schema(description = "MySQL 协议端口，缺省 9030")
        private Integer port;

        @Schema(description = "OTel 数据库名，缺省 otel")
        private String database;

        @Schema(description = "只读账号，缺省 otel_reader")
        private String username;

        @Schema(description = "只读账号密码")
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    /** 提交接管登记。 */
    @Data
    public static class Register {

        @Schema(description = "确认后的服务绑定关系")
        @NotEmpty(message = "未选择要接管的服务")
        private List<Binding> bindings;
    }

    @Data
    public static class Binding {

        @Schema(description = "Helm release 名")
        @NotBlank(message = "release 名不能为空")
        private String releaseName;

        @Schema(description = "所在命名空间")
        @NotBlank(message = "命名空间不能为空")
        private String namespace;

        @Schema(description = "绑定到的框架服务定义 ID")
        @NotNull(message = "未指定框架服务")
        private Integer frameServiceId;
    }
}
