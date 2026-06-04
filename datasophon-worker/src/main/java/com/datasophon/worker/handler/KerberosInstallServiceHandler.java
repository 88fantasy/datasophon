package com.datasophon.worker.handler;

import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.utils.TaskConstants;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zhanghuangbin
 */
public class KerberosInstallServiceHandler extends InstallServiceHandler {
    
    private Logger logger;
    
    @Override
    public void init(InstallServiceRoleCommand command) {
        super.init(command);
        logger = LoggerFactory.getLogger(TaskConstants.createLoggerName(serviceName, serviceRoleName, this.getClass()));
    }
    
    @Override
    public boolean match(InstallServiceRoleCommand command) {
        return command.getDecompressPackageName().contains("kerberos");
    }
    
    @Override
    public ExecResult install(InstallServiceRoleCommand command) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("yum");
        commands.add("install");
        commands.add("-y");
        if (ServiceRoleType.MASTER == command.getServiceRoleType()) {
            logger.info("Start to {}", commands);
            commands.add("krb5-server");
            commands.add("krb5-workstation");
            commands.add("krb5-libs");
        } else {
            logger.info("Start to {}", commands);
            commands.add("krb5-workstation");
            commands.add("krb5-libs");
        }
        ExecResult execResult = ShellUtils.execWithStatus(Constants.INSTALL_PATH, commands, 180, logger);
        if (execResult.getExecResult()) {
            return super.install(command);
        }
        return execResult;
    }
    
    protected String getLinkName(InstallServiceRoleCommand command) {
        // 无需创建软链
        return null;
    }
}
