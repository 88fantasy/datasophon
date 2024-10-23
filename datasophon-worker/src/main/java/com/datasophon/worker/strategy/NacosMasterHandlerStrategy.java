package com.datasophon.worker.strategy;

import cn.hutool.db.DbUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.handler.RsHandler;
import cn.hutool.db.sql.SqlExecutor;
import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.IOUtils;
import com.datasophon.worker.handler.ServiceHandler;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class NacosMasterHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy{

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
            String sqlPath = workPath + Constants.SLASH + "conf/nacos-mysql.sql";
            logger.info("check if nacos database is ready");
            logger.info("applicaitonPath:{}, sqlPath:{}", applicaitonPath, sqlPath);
            InputStream fis = null;
            Properties properties = new Properties();
            try {
                fis = this.getClass().getClassLoader().getResourceAsStream(applicaitonPath);
                properties.load(fis);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                if (fis != null) {
                    IOUtils.closeQuietly(fis);
                }
                System.exit(1);
            } finally {
                IOUtils.closeQuietly(fis);
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
                    //匹配一个表名，如果没有匹配到，说明数据库没有初始化
                    ready = false;
                }
                if (!ready) {
                    List<String> sqlList = com.datasophon.worker.utils.FileUtils.loadFileSQL(sqlPath);
                    con = DbUtil.use(new SimpleDataSource(url, user, password)).getConnection();
                    SqlExecutor.executeBatch(con, sqlList);
                    logger.info("nacos初始化数据库成功");
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                DbUtil.close(con);
            }
        }
        return serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command.getDecompressPackageName(), command.getRunAs());
    }
}
