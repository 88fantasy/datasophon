package com.datasophon.worker.strategy;

import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.worker.handler.ServiceHandler;
import com.datasophon.worker.utils.FileUtils;
import com.datasophon.worker.utils.SqlUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.db.DbUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.sql.SqlExecutor;

public class NacosMasterHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public NacosMasterHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) throws SQLException, ClassNotFoundException {
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        String workPath = PkgInstallPathUtils.getInstallHome(command);
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            ExecResult execResult = new ExecResult();
            // 判断数据库是否已经初始化
            boolean ready = true;
            String applicationPath = workPath + Constants.SLASH + "conf/application.properties";
            String pwdApplicationPath = workPath + Constants.SLASH + "conf/user-mgmt.properties";
            String sqlPath = workPath + Constants.SLASH + "conf/mysql-schema.sql";
            logger.info("check if nacos database is ready");
            logger.info("applicationPath:{}, sqlPath:{}", applicationPath, sqlPath);
            File applicationFile = new File(applicationPath);
            File pwdApplicationFile = new File(pwdApplicationPath);
            boolean isPasswordFileExists = pwdApplicationFile.exists();
            logger.info("isApplicationFileExists:{}, isPasswordFileExists:{}", applicationFile.exists(), isPasswordFileExists);
            if (applicationFile.exists()) {
                Properties properties = new Properties();
                try (FileInputStream fis = new FileInputStream(applicationFile)) {
                    properties.load(fis);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
                // 连接数据库 判断是否已经初始化数据库
                String jdbcUrl = properties.getProperty("db.url.0");
                String user = properties.getProperty("db.user.0");
                String password = properties.getProperty("db.password.0");
                String adminPassword = properties.getProperty("nacos.core.auth.server.identity.value");
                String encoderPwd = BCrypt.hashpw(adminPassword);
                logger.info("database info is using  {}  on {} ", user, jdbcUrl);
                Connection con = null;
                try {
                    con = DbUtil.use(new SimpleDataSource(jdbcUrl, user, password)).getConnection();
                    List<String> entityList = SqlUtils.getTableList(con);
                    if (entityList.stream().noneMatch("config_info"::equals)) {
                        // 匹配一个表名，如果没有匹配到，说明数据库没有初始化
                        ready = false;
                    }
                    if (!ready) {
                        List<String> sqlList = FileUtils.loadFileSQL(sqlPath);
                        con = DbUtil.use(new SimpleDataSource(jdbcUrl, user, password)).getConnection();
                        SqlExecutor.executeBatch(con, sqlList);
                        
                        SqlExecutor.execute(con, "INSERT INTO users (username, password, enabled) VALUES (?, ? , 1 )", "nacos", encoderPwd);
                        SqlExecutor.execute(con, "INSERT INTO roles (username, role) VALUES (?, ?)", "nacos", "ROLE_ADMIN");
                        
                        logger.info("initialize nacos database success");
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    execResult.setExecErrOut(e.getMessage());
                    return execResult;
                } finally {
                    DbUtil.close(con);
                }
            } else {
                logger.error("application.properties not found in {}", workPath);
                execResult.setExecErrOut("application.properties not found");
                return execResult;
            }
        }
        return serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command, command.getRunAs(), command.isCheckStatus());
    }
}
