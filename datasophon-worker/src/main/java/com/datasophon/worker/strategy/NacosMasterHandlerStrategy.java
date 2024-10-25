package com.datasophon.worker.strategy;

import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.worker.handler.ServiceHandler;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.db.DbUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.handler.RsHandler;
import cn.hutool.db.sql.SqlExecutor;

public class NacosMasterHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public NacosMasterHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) throws SQLException, ClassNotFoundException {
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        String workPath = Constants.INSTALL_PATH + Constants.SLASH + command.getDecompressPackageName();
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            // 判断数据库是否已经初始化
            boolean ready = true;
            String applicaitonPath = workPath + Constants.SLASH + "conf/application.properties";
            String pwdApplicationPath = workPath + Constants.SLASH + "conf/nacos-user-mgmt.properties";
            String sqlPath = workPath + Constants.SLASH + "conf/nacos-mysql.sql";
            logger.info("check if nacos database is ready");
            logger.info("applicaitonPath:{}, sqlPath:{}", applicaitonPath, sqlPath);
            File applicaitonFile = new File(applicaitonPath);
            File pwdApplicationFile = new File(pwdApplicationPath);
            boolean isPasswordFileExists = pwdApplicationFile.exists();
            logger.info("isApplicationFileExists:{}, isPasswordFileExists:{}", applicaitonFile.exists(), isPasswordFileExists);
            if (applicaitonFile.exists()) {
                Properties properties = new Properties();
                try (FileInputStream fis = new FileInputStream(applicaitonFile)) {
                    properties.load(fis);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
                // 连接数据库 判断是否已经初始化数据库
                String url = properties.getProperty("db.url.0");
                String user = properties.getProperty("db.user");
                String password = properties.getProperty("db.password");
                logger.info("database info is using  {}  on {} ", user, url);
                Connection con = null;
                try {
                    con = DbUtil.use(new SimpleDataSource(url, user, password)).getConnection();
                    List<String> entityList = SqlExecutor.query(con, "SHOW TABLES", new RsHandler<List<String>>() {
                        
                        @Override
                        public List<String> handle(ResultSet rs) throws SQLException {
                            final List<String> result = new ArrayList<>();
                            while (rs.next()) {
                                result.add(rs.getString(1));
                            }
                            return result;
                        }
                    });
                    if (entityList.stream().noneMatch(s -> s.equals("config_info"))) {
                        // 匹配一个表名，如果没有匹配到，说明数据库没有初始化
                        ready = false;
                    }
                    if (!ready) {
                        List<String> sqlList = com.datasophon.worker.utils.FileUtils.loadFileSQL(sqlPath);
                        con = DbUtil.use(new SimpleDataSource(url, user, password)).getConnection();
                        SqlExecutor.executeBatch(con, sqlList);
                        logger.info("initialize nacos database success");
                    }
                    
                    if (isPasswordFileExists) {
                        // 密码文件存在，说明需要修改密码
                        logger.info("nacos-user-mgmt.properties found in {}, modify password", pwdApplicationPath);
                        // 读取密码文件
                        Properties pwdProperties = new Properties();
                        try (FileInputStream fis = new FileInputStream(pwdApplicationPath)) {
                            pwdProperties.load(fis);
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                        }
                        String username = StringUtils.isEmpty(pwdProperties.getProperty("nacosUsername")) ? "nacos" : pwdProperties.getProperty("nacosUsername");
                        String nacosPassword = StringUtils.isEmpty(pwdProperties.getProperty("nacosPassword")) ? "nacos" : pwdProperties.getProperty("nacosPassword");
                        
                        String encoderPwd = BCrypt.hashpw(nacosPassword, BCrypt.gensalt());
                        // 查询用户表是否存在数据
                        List<String> userList = SqlExecutor.query(con, "SELECT * FROM users where username = '" + username + "'", new RsHandler<List<String>>() {
                            
                            @Override
                            public List<String> handle(ResultSet rs) throws SQLException {
                                final List<String> result = new ArrayList<>();
                                while (rs.next()) {
                                    result.add(rs.getString(1));
                                }
                                return result;
                            }
                        });
                        if (userList.isEmpty()) {
                            // 创建用户
                            SqlExecutor.execute(con, "INSERT INTO users (username, password, enabled) VALUES ('" + username + "', '" + encoderPwd + "',TRUE )");
                            logger.info("create nacos users success");
                        } else {
                            // 修改密码
                            SqlExecutor.execute(con, "UPDATE users SET password = '" + encoderPwd + "' WHERE username = '" + username + "'");
                            logger.info("update nacos users success");
                        }
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    DbUtil.close(con);
                }
                
            } else {
                logger.error("applicaiton.properties not found in {}", workPath);
            }
        }
        return serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command.getDecompressPackageName(), command.getRunAs());
    }
}
