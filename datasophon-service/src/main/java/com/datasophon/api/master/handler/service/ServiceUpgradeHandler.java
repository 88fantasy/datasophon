package com.datasophon.api.master.handler.service;

import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.ServicePkgNameUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.model.ArchInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterHostDO;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ServiceUpgradeHandler extends ServiceHandler {

    @Override
    public ExecResult handlerRequest(ServiceRoleInfo serviceRoleInfo) throws Exception {
        ExecResult execResult = new ExecResult();
        ClusterHostService clusterHostService = SpringTool.getApplicationContext().getBean(ClusterHostService.class);
        ClusterHostDO hostEntity = clusterHostService.getClusterHostByHostname(serviceRoleInfo.getHostname());


        InstallServiceRoleCommand installServiceRoleCommand = new InstallServiceRoleCommand();
        installServiceRoleCommand.setPackageName(serviceRoleInfo.getPackageName());
        installServiceRoleCommand.setFrameCode(serviceRoleInfo.getFrameCode());
        installServiceRoleCommand.setServiceName(serviceRoleInfo.getParentName());
        installServiceRoleCommand.setServiceRoleName(serviceRoleInfo.getName());
        installServiceRoleCommand.setServiceRoleType(serviceRoleInfo.getRoleType());
        installServiceRoleCommand.setDecompressPackageName(serviceRoleInfo.getDecompressPackageName());
        installServiceRoleCommand.setCreateDecompressDir(serviceRoleInfo.getCreateDecompressDir());
        installServiceRoleCommand.setRunAs(serviceRoleInfo.getRunAs());
        installServiceRoleCommand.setServiceRoleType(serviceRoleInfo.getRoleType());
        installServiceRoleCommand.setResourceStrategies(serviceRoleInfo.getResourceStrategies());
        installServiceRoleCommand.setVariables(GlobalVariables.getVariables(serviceRoleInfo.getClusterId()));


        String arch = hostEntity.getCpuArchitecture();
        ArchInfo archInfo = ServicePkgNameUtils.getArchInfo(serviceRoleInfo, arch);
        if (archInfo != null) {
            installServiceRoleCommand.setPackageName(archInfo.getPackageName());
            log.info("在host {} {} {}, 使用架构{}", serviceRoleInfo.getHostname(), serviceRoleInfo.getCommandType().getCommandName(Constants.CN),
                    serviceRoleInfo.getName(), arch);
        } else {
            log.error("在host {} {} {}, 无法匹配架构{}", serviceRoleInfo.getHostname(), serviceRoleInfo.getCommandType().getCommandName(Constants.CN),
                    serviceRoleInfo.getName(), arch);
            execResult.setExecOut("未找到满足系统架构 [" + arch + "] 的安装包 !");
            return execResult;
        }
        
        ActorSelection actorSelection = ActorUtils.actorSystem.actorSelection(
                "akka.tcp://datasophon@" + serviceRoleInfo.getHostname() + ":2552/user/worker/installServiceActor");
        Timeout timeout = new Timeout(Duration.create(180, TimeUnit.SECONDS));

        log.info("开始在主机{}执行{}{}命令", serviceRoleInfo.getHostname(), serviceRoleInfo.getCommandType().getCommandName(Constants.CN),
                serviceRoleInfo.getName());
        Future<Object> future = Patterns.ask(actorSelection, installServiceRoleCommand, timeout);
        try {
            ExecResult installResult = (ExecResult) Await.result(future, timeout.duration());
            return this.invokeNext(serviceRoleInfo, installResult);
        } catch (Exception e) {
            return new ExecResult();
        }
    }
}
