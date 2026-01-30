package com.datasophon.worker.handler;

import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

/**
 * @author zhanghuangbin
 */
public class HadoopInstallServiceHandler extends InstallServiceHandler{

    private static final String HADOOP = "hadoop";



    @Override
    public boolean match(InstallServiceRoleCommand command) {
        return command.getNormalPkgDir().contains(HADOOP);
    }


    @Override
    public ExecResult install(InstallServiceRoleCommand command) {
        ExecResult execResult = super.install(command);
        if (execResult.isSuccess()) {
            changeHadoopInstallPathPerm(command.getNormalPkgDir());
        }
        return execResult;
    }

    /**
     * 历史遗留代码的问题。比较合理，应该使用hook实现
     */
    private void changeHadoopInstallPathPerm(String decompressPackageName) {
        ShellUtils.execShell(" chown -R  root:hadoop " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
        ShellUtils.execShell(" chmod 775 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
        ShellUtils.execShell(" chmod -R 775 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + "/etc");
        ShellUtils.execShell(" chmod 6050 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + "/bin/container-executor");
        ShellUtils.execShell(" chmod 400 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + "/etc/hadoop/container-executor.cfg");
        ShellUtils.execShell(" chown -R yarn:hadoop " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + "/logs/userlogs");
        ShellUtils.execShell(" chmod 775 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + "/logs/userlogs");
    }


    @Override
    protected String getLinkName(InstallServiceRoleCommand command) {
        return HADOOP;
    }
}
