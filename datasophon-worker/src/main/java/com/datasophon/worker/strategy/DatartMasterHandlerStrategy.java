/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.worker.strategy;

import cn.hutool.db.DbUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.handler.RsHandler;
import cn.hutool.db.sql.SqlExecutor;
import cn.hutool.setting.yaml.YamlUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.worker.handler.ServiceHandler;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DatartMasterHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {

    public DatartMasterHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        String workPath = Constants.INSTALL_PATH + Constants.SLASH + command.getDecompressPackageName();
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            // 判断数据库是否已经初始化
            boolean ready = true;
            String applicaitonPath = workPath + Constants.SLASH + "config/datart.conf";
            String sqlPath = workPath + Constants.SLASH + "bin/migration/V2023.12.26__baseline.sql";
            logger.info("check if datart database is ready");
            logger.info("applicationPath:{}, sqlPath:{}", applicaitonPath, sqlPath);
            File applicationFile = new File(applicaitonPath);
            if (applicationFile.exists()) {
                Properties properties = new Properties();
                try (InputStream inputStream = Files.newInputStream(applicationFile.toPath())) {
                    properties.load(inputStream);
                } catch (Exception e) {
                    System.err.println("Failed to load the datart configuration (config/datart.conf), use application-config.yml");
                    properties =  new Properties();
                }
                Connection con = null;
                try {
                    String ip = properties.get("datasource.ip").toString();
                    String port = properties.get("datasource.port").toString();
                    String database = properties.get("datasource.database").toString();
                    String url = "jdbc:mysql://" + ip + ":" + port + "/" + database + "?&allowMultiQueries=true&characterEncoding=utf-8";
                    String username = properties.get("datasource.username").toString();
                    String password = properties.get("datasource.password").toString();
                    logger.info("database info is using  {}  on {} ", username, url);
                    con = DbUtil.use(new SimpleDataSource(url, username, password)).getConnection();
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
                    if (entityList.stream().noneMatch(s -> s.equals("source_schemas")
                            || s.equals("source") || s.equals("folder"))) {
                        ready = false;
                    }
                    if (!ready) {
                        List<String> sqlList = com.datasophon.worker.utils.FileUtils.loadFileSQL(sqlPath);
                        con = DbUtil.use(new SimpleDataSource(url, username, password)).getConnection();
                        SqlExecutor.executeBatch(con, sqlList);
                        logger.info("datart初始化数据库成功");
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    DbUtil.close(con);
                }
            }
        }
        return serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command.getDecompressPackageName(), command.getRunAs());
    }
    
}
