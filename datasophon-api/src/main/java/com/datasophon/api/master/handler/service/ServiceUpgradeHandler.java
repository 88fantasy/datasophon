package com.datasophon.api.master.handler.service;

import cn.hutool.core.collection.CollectionUtil;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.enums.HookType;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterHostDO;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ServiceUpgradeHandler extends ServiceHandler {

    @Override
    public ExecResult handlerRequest(ServiceRoleInfo serviceRoleInfo) throws Exception {
        ExecResult execResult = new ExecResult();
        ClusterHostService clusterHostService = SpringTool.getApplicationContext().getBean(ClusterHostService.class);
        ClusterHostDO hostEntity = clusterHostService.getClusterHostByHostname(serviceRoleInfo.getHostname());

        String packageName = resolvePackageName(serviceRoleInfo);
        if (packageName == null) {
            String arch = hostEntity.getCpuArchitecture();
            log.error("在host {} {} {}, 无法匹配架构{}", serviceRoleInfo.getHostname(), serviceRoleInfo.getCommandType().getCommandName(Constants.CN),
                    serviceRoleInfo.getName(), arch);
            execResult.setExecOut("未找到满足系统架构 [" + arch + "] 的安装包 !");
            return execResult;
        }
        log.info("在host {} {} {}, 使用包{}", serviceRoleInfo.getHostname(), serviceRoleInfo.getCommandType().getCommandName(Constants.CN),
                serviceRoleInfo.getName(), packageName);

        InstallServiceRoleCommand installServiceRoleCommand = new InstallServiceRoleCommand();
        installServiceRoleCommand.setFrameCode(serviceRoleInfo.getFrameCode());
        installServiceRoleCommand.setServiceName(serviceRoleInfo.getParentName());
        installServiceRoleCommand.setServiceRoleName(serviceRoleInfo.getName());
        installServiceRoleCommand.setServiceRoleType(serviceRoleInfo.getRoleType());
        installServiceRoleCommand.setPackageName(packageName);
        installServiceRoleCommand.setDecompressPackageName(serviceRoleInfo.getDecompressPackageName());
        installServiceRoleCommand.setCreateDecompressDir(serviceRoleInfo.getCreateDecompressDir());
        installServiceRoleCommand.setRunAs(serviceRoleInfo.getRunAs());
        installServiceRoleCommand.setServiceRoleType(serviceRoleInfo.getRoleType());
        installServiceRoleCommand.setVariables(createVariables(serviceRoleInfo));
        installServiceRoleCommand.setHooks(serviceRoleInfo.getMatchedHooks(HookType.PRE_INSTALL, HookType.POST_INSTALL));

        log.info("开始在主机{}执行{}{}命令", serviceRoleInfo.getHostname(),
                serviceRoleInfo.getCommandType().getCommandName(Constants.CN), serviceRoleInfo.getName());
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        ExecResult installResult = adapter.installServiceRole(serviceRoleInfo.getHostname(), installServiceRoleCommand);
        return this.invokeNext(serviceRoleInfo, installResult);
    }

    private Map<String, String> createVariables(ServiceRoleInfo roleInfo) {
        Map<String, String> variables = new HashMap<>(GlobalVariables.getVariables(roleInfo.getClusterId()));
        if (CollectionUtil.isNotEmpty(roleInfo.getConfigFileMap())) {
            List<ServiceConfig> configs = roleInfo.getConfigFileMap().values().iterator().next();
//            注意和ProcessUtils#createMergeVariables的逻辑保持一致
            configs.forEach(config-> {
                String name = config.getOriginalName();
//                如果存在占位符，则忽略(即不支持递归占位符)。
                if (name.contains("${") || Boolean.TRUE.equals(config.getRegister())) {
                    return;
                }
                if (config.getValue() instanceof String) {
                    variables.putIfAbsent(String.format("${%s.%s}", roleInfo.getParentName(), name), config.getValue().toString());
                    variables.putIfAbsent(String.format("${%s}", name), config.getValue().toString());
                }
            });
        }
        return variables;
    }
}
