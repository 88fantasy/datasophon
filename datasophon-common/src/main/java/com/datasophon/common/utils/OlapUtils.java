/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.common.utils;

import com.datasophon.common.Constants;
import com.datasophon.common.model.ProcInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OlapUtils {
    
    private static final String DOIRS_INSTALLED_PATH = Constants.INSTALL_PATH + Constants.SLASH + "doris";
    
    private static final Logger logger = LoggerFactory.getLogger(OlapUtils.class);
    
    public static ExecResult addFollower(String feMaster, String hostname, String rootPassword) {
        ExecResult execResult = new ExecResult();
        String sql = "ALTER SYSTEM add FOLLOWER \"" + hostname + ":9010\";";
        logger.info("Add fe to cluster , the sql is {}", sql);
        try {
            executeSql(feMaster, hostname, sql, rootPassword);
            execResult.setExecResult(true);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return execResult;
    }
    
    public static ExecResult addObserver(String feMaster, String hostname, String rootPassword) {
        ExecResult execResult = new ExecResult();
        String sql = "ALTER SYSTEM add OBSERVER \"" + hostname + ":9010\";";
        logger.info("Add fe to cluster , the sql is {}", sql);
        try {
            executeSql(feMaster, hostname, sql, rootPassword);
            execResult.setExecResult(true);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return execResult;
    }
    
    public static ExecResult addBackend(String feMaster, String hostname, String rootPassword) {
        ExecResult execResult = new ExecResult();
        String sql = "ALTER SYSTEM add BACKEND  \"" + hostname + ":9050\";";
        logger.info("Add be to cluster , the sql is {}", sql);
        
        try {
            executeSql(feMaster, hostname, sql, rootPassword);
            execResult.setExecResult(true);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return execResult;
    }
    
    private static void executeSql(String feMaster, String hostname,
                                   String sql, String rootPassword) throws ClassNotFoundException, SQLException {
        Connection connection = getConnection(feMaster, rootPassword);
        Statement statement = connection.createStatement();
        if (Objects.nonNull(connection) && Objects.nonNull(statement)) {
            statement.executeUpdate(sql);
        }
        close(connection, statement);
    }
    
    public static ExecResult addFollowerBySqlClient(String feMaster,
                                                    String hostname) {
        String sqlCommand =
                "mysql -h"
                        + feMaster
                        + " -uroot -P9030 -e"
                        + " 'ALTER SYSTEM add FOLLOWER  \""
                        + hostname
                        + ":9010\"';";
        // logger.info("sqlCommand is {}", sqlCommand);
        return ShellUtils.execShell(sqlCommand);
    }
    
    public static ExecResult addObserverBySqlClient(String feMaster,
                                                    String hostname) {
        String sqlCommand =
                "mysql -h"
                        + feMaster
                        + " -uroot -P9030 -e"
                        + " 'ALTER SYSTEM add OBSERVER  \""
                        + hostname
                        + ":9010\"';";
        // logger.info("sqlCommand is {}", sqlCommand);
        return ShellUtils.execShell(sqlCommand);
    }
    
    public static ExecResult addBackendBySqlClient(String feMaster,
                                                   String hostname) {
        String sqlCommand =
                "mysql -h"
                        + feMaster
                        + " -uroot -P9030 -e"
                        + " 'ALTER SYSTEM add BACKEND  \""
                        + hostname
                        + ":9050\"';";
        // logger.info("sqlCommand is {}", sqlCommand);
        return ShellUtils.execShell(sqlCommand);
    }
    
    private static Connection getConnection(String feMaster, String rootPassword) throws ClassNotFoundException, SQLException {
        Connection connection = null;
        String username = "root";
        String url = "jdbc:mysql://" + feMaster + ":9030";
        // 加载驱动
        Class.forName("com.mysql.cj.jdbc.Driver");
        // 获取安装路径
        // logger.info("doris fe配置密码 = {}", rootPassword);
        String password = rootPassword;
        try {
            connection = DriverManager.getConnection(url, username, password);
        } catch (Exception e) {
            logger.warn("doris密码可能还没初始化，使用空密码连接");
            // 部署初始化密码为空
            password = "";
            connection = DriverManager.getConnection(url, username, password);
        }
        return connection;
    }
    
    private static void close(Connection connection, Statement statement) throws SQLException {
        if (Objects.nonNull(connection) && Objects.nonNull(statement)) {
            statement.close();
            connection.close();
        }
    }
    
    public static List<ProcInfo> showFrontends(String feMaster, String rootPassword) throws SQLException, ClassNotFoundException {
        String sql = "SHOW PROC '/frontends';";
        // logger.info("sql is {}", sql);
        return executeQueryProcInfo(feMaster, sql, rootPassword);
    }
    
    public static List<ProcInfo> listDeadFrontends(String feMaster, String rootPassword) throws SQLException, ClassNotFoundException {
        String sql = "SHOW PROC '/frontends';";
        // logger.info("sql is {}", sql);
        return getDeadProcInfos(feMaster, sql, rootPassword);
    }
    
    public static List<ProcInfo> listDeadBackends(String feMaster, String rootPassword) throws SQLException, ClassNotFoundException {
        String sql = "SHOW PROC '/frontends';";
        // logger.info("sql is {}",sql);
        return getDeadProcInfos(feMaster, sql, rootPassword);
    }
    
    public static List<ProcInfo> showBackends(String feMaster, String rootPassword) throws SQLException, ClassNotFoundException {
        String sql = "SHOW PROC '/backends';";
        // logger.info("sql is {}",sql);
        return executeQueryProcInfo(feMaster, sql, rootPassword);
    }
    
    public static List<ProcInfo> executeQueryProcInfo(String feMaster,
                                                      String sql, String rootPassword) throws SQLException, ClassNotFoundException {
        Connection connection = getConnection(feMaster, rootPassword);
        Statement statement = connection.createStatement();
        ArrayList<ProcInfo> list = new ArrayList<>();
        if (Objects.nonNull(connection) && Objects.nonNull(statement)) {
            ResultSet resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                ProcInfo procInfo = new ProcInfo();
                procInfo.setIp(resultSet.getString("Host"));
                procInfo.setAlive(resultSet.getBoolean("Alive"));
                procInfo.setErrMsg(resultSet.getString("ErrMsg"));
                list.add(procInfo);
            }
        }
        close(connection, statement);
        return list;
    }
    
    public static List<ProcInfo> executeQuerySql(String feMaster,
                                                 String sql, String rootPassword) throws SQLException, ClassNotFoundException {
        Connection connection = getConnection(feMaster, rootPassword);
        long start = System.currentTimeMillis();
        Statement statement = connection.createStatement();
        ArrayList<ProcInfo> list = new ArrayList<>();
        if (Objects.nonNull(connection) && Objects.nonNull(statement)) {
            ResultSet resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                System.out.println(resultSet.getString(1));
            }
        }
        long end = System.currentTimeMillis();
        System.out.println(end - start);
        close(connection, statement);
        return list;
    }
    
    private static List<ProcInfo> getDeadProcInfos(String feMaster,
                                                   String sql, String rootPassword) throws SQLException, ClassNotFoundException {
        List<ProcInfo> list = executeQueryProcInfo(feMaster, sql, rootPassword);
        ArrayList<ProcInfo> deadList = new ArrayList<>();
        for (ProcInfo procInfo : list) {
            if (!procInfo.getAlive()) {
                deadList.add(procInfo);
            }
        }
        return deadList;
    }
    
    public static String getUniPassword(String feUniConfPath) {
        String root_password = "";
        File feUniConFile = new File(feUniConfPath);
        if (feUniConFile.exists()) {
            Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream(feUniConFile)) {
                properties.load(fis);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
            if (Objects.nonNull(properties.getProperty("root_password"))) {
                root_password = properties.getProperty("root_password");
            }
        }
        return root_password;
    }
    
}
