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

package com.datasophon.worker.strategy;

import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.handler.ServiceHandler;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import cn.hutool.db.DbUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.handler.RsHandler;
import cn.hutool.db.sql.SqlExecutor;

public class HiveMetaStoreHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public HiveMetaStoreHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ExecResult startResult;
        final String workPath = PkgInstallPathUtils.getInstallHome(command);
        final String hiveSitePath = workPath + Constants.SLASH + "conf" + Constants.SLASH + "hive-site.xml";
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            File hiveSiteFile = new File(hiveSitePath);
            if (hiveSiteFile.exists()) {
                startResult = initDb(workPath, hiveSitePath);
                if (!startResult.isSuccess()) {
                    return startResult;
                }
            }
        }
        return serviceHandler.start(command.getStartRunner(), command.getStatusRunner(), command, command.getRunAs(), command.isCheckStatus());
    }
    
    private ExecResult initDb(String workPath, String hiveSitePath) {
        boolean ready = true;
        Map<String, String> hiveSiteProps = getHiveSiteProps(hiveSitePath);
        String url = hiveSiteProps.get("javax.jdo.option.ConnectionURL");
        String username = hiveSiteProps.get("javax.jdo.option.ConnectionUserName");
        String password = hiveSiteProps.get("javax.jdo.option.ConnectionPassword");
        logger.info("database info is using  {}  on {} ", username, url);
        Connection con = null;
        try {
            con = DbUtil.use(new SimpleDataSource(url, username, password)).getConnection();
            List<String> entityList = SqlExecutor.query(con, "SHOW TABLES",
                    (RsHandler<List<String>>) rs -> {
                        final List<String> result = new ArrayList<>();
                        while (rs.next()) {
                            result.add(rs.getString(1));
                        }
                        return result;
                    });
            if (entityList.stream().noneMatch(s -> s.equals("VERSION"))) {
                ready = false;
            }
            if (!ready) {
                String initCmd = String.format("%s/bin/schematool -dbType mysql -initSchema", workPath);
                logger.info("start to init hive schema:{}", initCmd);
                ExecResult result = ShellUtils.execShell(initCmd);
                if (result.isSuccess()) {
                    logger.info("hive初始化数据库成功");
                } else {
                    logger.error("hive初始化数据库失败，原因：{}", result.getExecResult());
                    return result;
                }
            }
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
        } catch (Exception e) {
            logger.error("hive初始化数据库失败, ", e);
            return ExecResult.error("hive初始化数据库失败, " + e.getMessage());
        } finally {
            DbUtil.close(con);
        }
        return ExecResult.success();
    }
    
    private Map<String, String> getHiveSiteProps(String hiveSitePath) {
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
            throw new IllegalStateException(String.format("解析文件%s失败，%s", hiveSitePath, e.getMessage()), e);
        }
        return props;
    }
}
