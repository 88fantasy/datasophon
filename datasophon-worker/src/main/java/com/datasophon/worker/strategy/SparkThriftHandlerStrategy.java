package com.datasophon.worker.strategy;

import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.handler.ServiceHandler;

import java.sql.SQLException;

/**
 * spark3
 * spark on yarn
 * @author liushumin
 * @since 2024-11-29 11:30
 */
public class SparkThriftHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {

    public SparkThriftHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) throws SQLException, ClassNotFoundException {
        ExecResult startResult = new ExecResult();
        String workPath = PkgInstallPathUtils.getInstallHome(command);
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            // hdfs jars
            String hdfsSparkJarsPath = "/spark3/jars";
            String localSparkJarsPath = workPath + Constants.SLASH + "jars";
            String hadoopHome = PropertyUtils.getString("HADOOP_HOME");
            ExecResult result = ShellUtils.execShell(String.format("sudo -u hdfs %s/bin/hdfs dfs -ls %s | wc -l", hadoopHome, hdfsSparkJarsPath));
            if(result.getExecResult() && Integer.parseInt(result.getExecOut()) > 0) {
                logger.info("hdfs {}已初始化", hdfsSparkJarsPath);
            } else {
                ShellUtils.execShell(String.format("sudo -u hdfs %s/bin/hdfs dfs -mkdir -p %s", hadoopHome, hdfsSparkJarsPath));
                ShellUtils.execShell(String.format("sudo -u hdfs %s/bin/hdfs dfs -put %s/* %s", hadoopHome, localSparkJarsPath, hdfsSparkJarsPath));
            }
        }
        startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command.getDecompressPackageName(), command.getRunAs(), command.isCheckStatus());
        return startResult;
    }
}
