package com.datasophon.worker.handler;

import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.worker.utils.TaskConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


/**
 * 特殊的定制逻辑, yarn使用的是hadoop的安装包，所以不需要解压，否则会覆盖hadoop原来的安装包
 * @author zhanghuangbin
 */
public class YarnInstallServiceHandler extends InstallServiceHandler {

    private static final String YARN = "yarn";

    private Logger logger;

//    优先hadoop执行
    public static final int ORDER = HadoopInstallServiceHandler.ORDER - 100;

    @Override
    public void init(InstallServiceRoleCommand command){
        super.init(command);
        logger = LoggerFactory.getLogger(TaskConstants.createLoggerName(serviceName, serviceRoleName, this.getClass()));
    }


    @Override
    public boolean match(InstallServiceRoleCommand command) {
        return command.getServiceName().equalsIgnoreCase(YARN);
    }


    @Override
    public ExecResult install(InstallServiceRoleCommand command) {
        String linkName = getLinkName(command);
        if (command.getNormalPkgDir().equals(linkName)) {
            throw new IllegalStateException(String.format("软件%s安装目录和软链目录名字一致，无法解压", command.getServiceName()));
        }
        File hadoopDir = new File(Constants.INSTALL_PATH + Constants.SLASH + command.getNormalPkgDir());
        if (!hadoopDir.exists()) {
            logger.error("yarn依赖于hadoop的安装包，目录{}不存在", hadoopDir.getAbsolutePath());
            return ExecResult.error(String.format("yarn依赖于hadoop的安装包，目录%s不存在", hadoopDir.getAbsolutePath()));
        }
        ExecResult execResult = new ExecResult();
        try {
//            yarn直接复用hadoop安装包，无需解压安装包
            logger.info("注意！！！yarn复用hadoop的安装包,无需安装。软件包路径：{}", hadoopDir.getAbsolutePath());
            return execResourceStrategies(command, logger);
        } catch (Exception e) {
            logger.error("安装服务{} {}失败,  {}", command.getServiceName(), command.getServiceRoleName(), e.getMessage(), e);
            execResult.setExecOut(e.getMessage());
        }
        return execResult;
    }



    @Override
    public int getOrder() {
        return ORDER;
    }


}
