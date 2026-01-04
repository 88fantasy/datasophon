package com.datasophon.api.vo.extrepo;

import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.ArchInfo;
import com.datasophon.common.model.ExternalLink;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.RunAs;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleRunner;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.enums.RoleType;
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.enums.dag.NodeStatus;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
public class InstallProgressDAG2 implements scala.Serializable {



    @Schema(description = "dagId")
    private String id;

    @Schema(description = "dag状态")
    private DagStatus status;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "开始运行时间")
    private LocalDateTime startedTime;

    @Schema(description = "结束运行时间")
    private LocalDateTime completedTime;

    @Schema(description = "安装节点")
    private List<Node> nodes;


    @Schema(description = "边")
    private List<EdgeVO> edges = new ArrayList<>(0);


    @Data
    public static class Node implements Serializable {

        @Schema(description = "节点ID")
        private String id;

        @Schema(description = "dagId")
        private String dagId;

        @Schema(description = "节点名称")
        private String nodeName;

        @Schema(description = "运行状态")
        private NodeStatus status;

        @Schema(description = "创建时间")
        private LocalDateTime createdTime;

        @Schema(description = "开始运行时间")
        private LocalDateTime startedTime;

        @Schema(description = "结束运行时间")
        private LocalDateTime completedTime;

        @Schema(description = "运行说明")
        private String executionLog;

        @Schema(description = "命令行ID")
        private String commandId;

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
        private String commandState;

        @Schema(description = "指令状态名称")
        private String commandStateName;

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
        private String id;

        @Schema(description = "开始节点ID")
        private String start;

        @Schema(description = "结束节点ID")
        private String end;


    }


}
