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

import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.handler.ServiceHandler;
import com.datasophon.worker.utils.SqlUtils;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.db.DbUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;

public class DSMasterHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public DSMasterHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        String workPath = PkgInstallPathUtils.getInstallHome(command);
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            // 判断数据库是否已经初始化
            boolean ready = true;
            logger.info("check if DolphinScheduler database is ready ");
            // 读取ds元数据库地址和账密
            File envFile = new File(workPath + "/bin/env/dolphinscheduler_env.sh");
            if (envFile.exists()) {
                List<String> lines = FileReader.create(envFile).readLines();
                Optional<String> optionalUrl =
                        lines.stream().filter(line -> line.contains("SPRING_DATASOURCE_URL")).findFirst();
                Optional<String> optionalUsername =
                        lines.stream().filter(line -> line.contains("SPRING_DATASOURCE_USERNAME")).findFirst();
                Optional<String> optionalPassword =
                        lines.stream().filter(line -> line.contains("SPRING_DATASOURCE_PASSWORD")).findFirst();
                
                if (optionalUrl.isPresent() && optionalUsername.isPresent() && optionalPassword.isPresent()) {
                    String url = getValue(optionalUrl.get());
                    String username = getValue(optionalUsername.get());
                    String password = getValue(optionalPassword.get());
                    logger.info("database info is using  {}  on {} ", username, url);
                    Connection con = null;
                    try {
                        con = DbUtil.use(new SimpleDataSource(url, username, password)).getConnection();
                        List<String> entityList = SqlUtils.getTableList(con);
                        if (entityList.stream().noneMatch(s -> s.equals("t_escheduler_version")
                                || s.equals("t_ds_version") || s.equals("t_escheduler_queue"))) {
                            ready = false;
                        }
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    } finally {
                        DbUtil.close(con);
                    }
                }
            }
            
            if (!ready) {
                ArrayList<String> commands = new ArrayList<>();
                commands.add("bash");
                commands.add("tools/bin/upgrade-schema.sh");
                ExecResult execResult = ShellUtils.execWithStatus(workPath, commands, 180L, logger);
                if (execResult.getExecResult()) {
                    logger.info("DolphinScheduler database init success");
                } else {
                    logger.error("DolphinScheduler database init failed");
                    return execResult;
                }
            }
        }
        
        return serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command, command.getRunAs(), command.isCheckStatus());
    }
    
    private String getValue(String line) {
        String value = line.substring(line.indexOf("=") + 1);
        if (value.startsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
    
}
