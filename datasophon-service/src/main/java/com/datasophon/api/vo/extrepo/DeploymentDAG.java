package com.datasophon.api.vo.extrepo;

import com.datasophon.dao.model.extrepo.DeploySrvRoleModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
public class DeploymentDAG implements scala.Serializable {

    @Schema(description = "节点")
    private List<SrvNodeVO> nodes = new ArrayList<>();

    @Schema(description = "边")
    private List<EdgeVO> edge = new ArrayList<>(0);


    @Data
    public static class SrvNodeVO implements Serializable {

        @Schema(description = "节点ID")
        private Integer id;

        @Schema(description = "服务名称")
        private String name;

        @Schema(description = "服务版本")
        private String version;

        @Schema(description = "服务说明")
        private String type;

        @Schema(description = "服务说明")
        private String desc;

        @Schema(description = "服务状态 0表示需要安装 1表示就绪 2表示依赖软件未安装")
        private Integer state;

        @Schema(description = "服务下的角色信息")
        private List<DeploySrvRoleModel> roles;
    }

    @Data
    public static class EdgeVO  implements Serializable{

        @Schema(description = "连线id")
        private Integer id;

        @Schema(description = "开始节点ID")
        private Integer start;

        @Schema(description = "结束节点ID")
        private Integer end;


    }



}
