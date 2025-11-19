package com.datasophon.api.vo.extrepo;

import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.enums.RoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
public class InstallProgressDAG implements scala.Serializable {

    @Schema(description = "服务节点")
    private List<Srv> srvList = new ArrayList<>();

    @Schema(description = "边")
    private List<EdgeVO> edge = new ArrayList<>(0);


    @Data
    public static class Srv implements Serializable {

        private Integer id;

        @Schema(description = "命令ID")
        private String cmdId;

        @Schema(description = "服务名称")
        private String name;



        @Schema(description = "命令行状态")
        private CommandState commandState;

        @Schema(description = "服务下的角色信息")
        private List<SrvRole> roles;
    }


    @Data
    public static class SrvRole {

        @Schema(description = "角色名称")
        private String roleName;


        @Schema(description = "命令列表")
        private List<HostCmd> cmdList;

    }

    @Data
    public static class HostCmd {

        @Schema(description = "主键")
        private String hostCommandId;

        @Schema(description = "指令名称")
        private String commandName;
        /**
         * 指令状态 1、正在运行2：成功3：失败
         */
        @Schema(description = "指令状态")
        private CommandState commandState;

        @Schema(description = "指令进度")
        private Integer commandProgress;


        @Schema(description = "主机名")
        private String hostname;

        @Schema(description = "服务角色名称")
        private String serviceRoleName;

        @Schema(description = "角色类型")
        private RoleType serviceRoleType;

        @Schema(description = "执行信息")
        private String resultMsg;

        @Schema(description = "创建时间")
        private Date createTime;

        @Schema(description = "命令类型")
        private Integer commandType;
    }

    @Data
    public static class EdgeVO implements Serializable {

        @Schema(description = "连线id")
        private Integer id;

        @Schema(description = "开始节点ID")
        private Integer start;

        @Schema(description = "结束节点ID")
        private Integer end;


    }


}
