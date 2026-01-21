package com.datasophon.api.vo.extrepo;

import com.datasophon.dao.model.extrepo.DeploySrvRoleModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
    @EqualsAndHashCode(callSuper = true)
    public static class SrvNodeVO extends DAGNode {

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
