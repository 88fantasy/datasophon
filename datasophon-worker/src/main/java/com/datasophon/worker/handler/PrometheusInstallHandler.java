package com.datasophon.worker.handler;

import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;

/**
 * @author zhanghuangbin
 */
public class PrometheusInstallHandler extends InstallServiceHandler {
    
    @Override
    public boolean match(InstallServiceRoleCommand command) {
        return command.getNormalPkgDir().contains(Constants.PROMETHEUS);
    }
    
    @Override
    public ExecResult install(InstallServiceRoleCommand command) {
        ExecResult execResult = super.install(command);
        if (execResult.isSuccess()) {
            // 历史代码遗留问题，推荐hook实现
            String alertPath = Constants.INSTALL_PATH + Constants.SLASH + command.getNormalPkgDir() + Constants.SLASH + "alert_rules";
            ShellUtils.execShell("sed -i \"s/clusterIdValue/" + PropertyUtils.getString("clusterId") + "/g\" `grep clusterIdValue -rl " + alertPath + "`");
        }
        return execResult;
    }
}
