/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.master.service;

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.observability.OtelCollectorConfigService;
import com.datasophon.api.service.ClusterAlertQuotaService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceWebuisService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostService;
import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.common.Constants;
import com.datasophon.common.command.HdfsEcCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.UpdateCommandHostMessage;
import com.datasophon.dao.entity.ClusterAlertQuota;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceWebuis;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostEntity;
import com.datasophon.dao.enums.CommandState;

import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * 服务命令完成处理 Spring Service，业务逻辑来自 {@link ServiceCommandActor}。
 * 内部调用 {@link HdfsECService} 和 {@link OtelCollectorConfigService}（均为 Spring Bean）。
 */
@Service
public class ServiceCommandService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceCommandService.class);

    private static final String STARROCKS = "starrocks";
    private static final String DORIS = "doris";
    private static final String HDFS = "hdfs";
    private static final String ENABLE_HDFS_KERBEROS = "${enableHDFSKerberos}";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String NODE = "NODE";

    private final ClusterServiceCommandHostCommandService hostCommandService;
    private final ClusterServiceCommandHostService commandHostService;
    private final ClusterServiceCommandService commandService;
    private final ClusterInfoService clusterInfoService;
    private final ClusterAlertQuotaService alertQuotaService;
    private final ClusterServiceRoleInstanceWebuisService webuisService;
    private final HdfsECService hdfsECService;
    private final OtelCollectorConfigService otelCollectorConfigService;

    public ServiceCommandService(ClusterServiceCommandHostCommandService hostCommandService,
                                 ClusterServiceCommandHostService commandHostService,
                                 ClusterServiceCommandService commandService,
                                 ClusterInfoService clusterInfoService,
                                 ClusterAlertQuotaService alertQuotaService,
                                 ClusterServiceRoleInstanceWebuisService webuisService,
                                 HdfsECService hdfsECService,
                                 OtelCollectorConfigService otelCollectorConfigService) {
        this.hostCommandService = hostCommandService;
        this.commandHostService = commandHostService;
        this.commandService = commandService;
        this.clusterInfoService = clusterInfoService;
        this.alertQuotaService = alertQuotaService;
        this.webuisService = webuisService;
        this.hdfsECService = hdfsECService;
        this.otelCollectorConfigService = otelCollectorConfigService;
    }

    /**
     * 异步处理主机命令完成通知（替代 ServiceCommandActor.tell(message)）。
     */
    @Async("masterExecutor")
    public void updateCommandHost(UpdateCommandHostMessage message) {
        doUpdateCommandHost(message);
        doUpdateCommand(message);
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private void doUpdateCommandHost(UpdateCommandHostMessage message) {
        ClusterServiceCommandHostEntity commandHost = commandHostService.getOne(
                new QueryWrapper<ClusterServiceCommandHostEntity>()
                        .eq(Constants.COMMAND_HOST_ID, message.getCommandHostId()));

        Long size = hostCommandService.getHostCommandSizeByHostnameAndCommandHostId(
                message.getHostname(), message.getCommandHostId());
        Integer totalProgress = hostCommandService.getHostCommandTotalProgressByHostnameAndCommandHostId(
                message.getHostname(), message.getCommandHostId());
        long progress = totalProgress / size;
        commandHost.setCommandProgress((int) progress);

        List<ClusterServiceCommandHostCommandEntity> failList =
                hostCommandService.findFailedHostCommand(message.getHostname(), message.getCommandHostId());
        List<ClusterServiceCommandHostCommandEntity> cancelList =
                hostCommandService.findCanceledHostCommand(message.getHostname(), message.getCommandHostId());

        if (!failList.isEmpty()) {
            commandHost.setCommandState(CommandState.FAILED);
        } else if (!cancelList.isEmpty()) {
            commandHost.setCommandState(CommandState.CANCEL);
        } else if (progress == 100) {
            commandHost.setCommandState(CommandState.SUCCESS);
        }
        commandHostService.update(commandHost,
                new QueryWrapper<ClusterServiceCommandHostEntity>()
                        .eq(Constants.COMMAND_HOST_ID, message.getCommandHostId()));
    }

    private void doUpdateCommand(UpdateCommandHostMessage message) {
        Long size = commandHostService.getCommandHostSizeByCommandId(message.getCommandId());
        Integer totalProgress = commandHostService.getCommandHostTotalProgressByCommandId(message.getCommandId());
        long progress = totalProgress / size;

        ClusterServiceCommandEntity command = commandService.lambdaQuery()
                .eq(ClusterServiceCommandEntity::getCommandId, message.getCommandId())
                .one();
        command.setCommandProgress((int) progress);

        List<ClusterServiceCommandHostEntity> failList =
                commandHostService.findFailedCommandHost(message.getCommandId());
        List<ClusterServiceCommandHostEntity> cancelList =
                commandHostService.findCanceledCommandHost(message.getCommandId());

        if (!failList.isEmpty()) {
            command.setCommandState(CommandState.FAILED);
            command.setEndTime(new Date());
        } else if (!cancelList.isEmpty()) {
            command.setCommandState(CommandState.CANCEL);
            command.setEndTime(new Date());
        } else if (progress == 100) {
            command.setCommandState(CommandState.SUCCESS);
            command.setEndTime(new Date());

            String serviceName = command.getServiceName();
            ClusterInfoEntity clusterInfo = clusterInfoService.getById(command.getClusterId());

            if (command.getCommandType() == 4 && HDFS.equalsIgnoreCase(serviceName)) {
                updateHDFSWebUi(clusterInfo.getId(), command.getServiceInstanceId());
            }

            if (command.getCommandType() == 1) {
                if (HDFS.equalsIgnoreCase(serviceName)) {
                    HdfsEcCommand hdfsEcCommand = new HdfsEcCommand();
                    hdfsEcCommand.setServiceInstanceId(command.getServiceInstanceId());
                    hdfsECService.manageHdfsEC(hdfsEcCommand);
                }

                if (!STARROCKS.equalsIgnoreCase(serviceName) && !DORIS.equalsIgnoreCase(serviceName)) {
                    enableAlertConfig(NODE, clusterInfo.getId());
                }
                enableAlertConfig(serviceName, clusterInfo.getId());
            }

            if (shouldRefreshOtelCollectors(command.getCommandType())) {
                refreshAffectedOtelCollectors(command);
            }
        }

        commandService.lambdaUpdate()
                .eq(ClusterServiceCommandEntity::getCommandId, command.getCommandId())
                .update(command);
    }

    private void enableAlertConfig(String serviceName, Integer clusterId) {
        List<ClusterAlertQuota> list = alertQuotaService.listAlertQuotaByServiceName(serviceName);
        List<Integer> ids = list.stream().map(ClusterAlertQuota::getId).toList();
        String alertQuotaIds = StringUtils.join(ids, ",");
        alertQuotaService.start(clusterId, alertQuotaIds);
    }

    private void updateHDFSWebUi(Integer clusterId, Integer serviceInstanceId) {
        Map<String, String> variables = GlobalVariables.getVariables(clusterId);
        if (variables.containsKey(ENABLE_HDFS_KERBEROS)) {
            List<ClusterServiceRoleInstanceWebuis> webUis =
                    webuisService.listWebUisByServiceInstanceId(serviceInstanceId);
            for (ClusterServiceRoleInstanceWebuis webUi : webUis) {
                if (TRUE.equals(variables.get(ENABLE_HDFS_KERBEROS)) && webUi.getWebUrl().contains("9870")) {
                    webUi.setWebUrl(webUi.getWebUrl().replace(HTTP, HTTPS).replace("9870", "9871"));
                    webuisService.updateById(webUi);
                }
                if (FALSE.equals(variables.get(ENABLE_HDFS_KERBEROS)) && webUi.getWebUrl().contains("9871")) {
                    webUi.setWebUrl(webUi.getWebUrl().replace(HTTPS, HTTP).replace("9871", "9870"));
                    webuisService.updateById(webUi);
                }
            }
        }
    }

    private boolean shouldRefreshOtelCollectors(Integer commandType) {
        CommandType type = CommandType.ofCode(commandType);
        return CommandType.START_SERVICE.equals(type)
                || CommandType.STOP_SERVICE.equals(type)
                || CommandType.RESTART_SERVICE.equals(type)
                || CommandType.START_WITH_CONFIG.equals(type)
                || CommandType.RESTART_WITH_CONFIG.equals(type);
    }

    private void refreshAffectedOtelCollectors(ClusterServiceCommandEntity command) {
        hostCommandService.getHostCommandListByCommandId(command.getCommandId()).stream()
                .map(ClusterServiceCommandHostCommandEntity::getHostname)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .forEach(hostname -> otelCollectorConfigService.pushNodeConfig(
                        command.getClusterId(), hostname, Map.of()));
    }
}
