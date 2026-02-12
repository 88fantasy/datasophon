package com.datasophon.worker.handler;

import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;


/**
 * 特殊的定制逻辑，本来应该通过hook实现比较优雅，但是，历史代码太多，还是保留原来的作废
 * @author zhanghuangbin
 */
public class HadoopInstallServiceHandler extends InstallServiceHandler {

    private static final String HADOOP = "hadoop";

    public static final int ORDER = 1000;
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
    public ExecResult createLink(InstallServiceRoleCommand command) {
        ExecResult result = super.createLink(command);
        if (result.isSuccess()) {
//            旧逻辑，很多脚本，都用了这个地址，为了简单起见，仍然使用这个软链
            result = doCreateLink(Constants.INSTALL_PATH + Constants.SLASH + HADOOP, Constants.INSTALL_PATH + Constants.SLASH + command.getNormalPkgDir());
        }
        return result;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
