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
import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.ServiceRoleRunner;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ThrowableUtils;
import com.datasophon.worker.handler.ServiceHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HiveMetaStoreHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public HiveMetaStoreHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ExecResult startResult = new ExecResult();
        final String workPath = PkgInstallPathUtils.getInstallHome(command);
        final String hiveSitePath = workPath + Constants.SLASH + "conf" + Constants.SLASH + "hive-site.xml";
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        // 判断数据库是否已经初始化
        boolean ready = true;
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            File hiveSiteFile = new File(hiveSitePath);
            if(hiveSiteFile.exists()) {
                Map<String, String> hiveSiteProps = getHiveSiteProps(hiveSitePath);
                String url = hiveSiteProps.get("javax.jdo.option.ConnectionURL");
                String username = hiveSiteProps.get("javax.jdo.option.ConnectionUserName");
                String password = hiveSiteProps.get("javax.jdo.option.ConnectionPassword");
                logger.info("database info is using  {}  on {} ", username, url);
                Connection con = null;
                /**
                 */
                try {
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
                    if (entityList.stream().noneMatch(s -> s.equals("VERSION"))) {
                        ready = false;
                    }
                    if (!ready) {
                        // init hive database
                        logger.info("start to init hive schema");
                        ArrayList<String> commands = new ArrayList<>();
                        commands.add("bin/schematool");
                        commands.add("-dbType");
                        commands.add("mysql");
                        commands.add("-initSchema");

                        ServiceRoleRunner startRunner = new ServiceRoleRunner();
                        startRunner.setProgram(command.getStartRunner().getProgram());
                        startRunner.setArgs(commands);
                        startRunner.setTimeout("600");
                        startResult = serviceHandler.start(startRunner, command.getStatusRunner(),
                                command.getDecompressPackageName(), command.getRunAs());
                        if(startResult.getExecResult()) {
                            ready = true;
                            logger.info("hive初始化数据库成功");
                        } else {
                            logger.error("hive初始化数据库失败" + startResult.getExecErrOut());
                        }
                    }
                    if(ready) {
                        con = DbUtil.use(new SimpleDataSource(url, username, password)).getConnection();
                        // 修改表字段注解和表注解
                        SqlExecutor.execute(con, "ALTER TABLE `COLUMNS_V2` MODIFY COLUMN `COMMENT` varchar(256) CHARACTER SET utf8");
                        SqlExecutor.execute(con, "ALTER TABLE `COLUMNS_V2` MODIFY COLUMN `COLUMN_NAME` varchar(767) CHARACTER SET utf8");
                        SqlExecutor.execute(con, "ALTER TABLE `TABLE_PARAMS` MODIFY COLUMN `PARAM_VALUE` mediumtext CHARACTER SET utf8");
                        // 修改分区字段注解
                        SqlExecutor.execute(con, "ALTER TABLE `PARTITION_PARAMS` MODIFY COLUMN `PARAM_VALUE` varchar(4000) CHARACTER SET utf8");
                        SqlExecutor.execute(con, "ALTER TABLE `PARTITION_KEYS` MODIFY COLUMN `PKEY_COMMENT` varchar(4000) CHARACTER SET utf8");
                        // 修改索引注解
                        SqlExecutor.execute(con, "ALTER TABLE `INDEX_PARAMS` MODIFY COLUMN `PARAM_VALUE` varchar(4000) CHARACTER SET utf8");
                        logger.info("hive schema Chinese optimize 完成");
                    } else {
                        logger.error("hive表不存在,请先初始化hive表: bin/schematool -dbType mysql -initSchema");
                    }
                } catch (Exception e) {
                    logger.info("hive初始化数据库失败");
                    logger.error(e.getMessage(), e);
                } finally {
                    DbUtil.close(con);
                }
            }
        } else {
            startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                    command.getDecompressPackageName(), command.getRunAs());
        }
        return startResult;
    }

    private Map<String, String> getHiveSiteProps(String hiveSitePath){
        Map<String, String> props = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(hiveSitePath);
            Element root = document.getDocumentElement();

            NodeList propertyNodes = root.getElementsByTagName("property");
            for (int i = 0; i < propertyNodes.getLength(); i++) {
                Element node = (Element) propertyNodes.item(i);
                String name = node.getElementsByTagName("name").item(0).getFirstChild().getNodeValue();
                String value = node.getElementsByTagName("value").item(0).getFirstChild().getNodeValue();
                props.put(name, value);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return props;
    }
}
