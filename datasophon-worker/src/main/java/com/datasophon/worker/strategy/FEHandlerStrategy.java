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
import com.datasophon.common.model.ServiceRoleRunner;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.HostUtils;
import com.datasophon.common.utils.OlapUtils;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ThrowableUtils;
import com.datasophon.grpc.api.OlapNodeType;
import com.datasophon.worker.grpc.MasterCallbackClient;
import com.datasophon.worker.handler.ServiceHandler;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.util.ArrayList;

import cn.hutool.core.net.NetUtil;
import cn.hutool.db.DbUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.sql.SqlExecutor;
import cn.hutool.setting.dialect.Props;

public class FEHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public FEHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ExecResult startResult = new ExecResult();
        logger.info("FEHandlerStrategy start fe, command type is {}", command.getCommandType());
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        String workPath = PkgInstallPathUtils.getInstallHome(command);
        String feUniConfPath = workPath + "/fe/conf/fe.uni.conf";
        if (command.getCommandType() == CommandType.INSTALL_SERVICE) {
            if (command.isSlave()) {
                logger.info("第一次启动FE, 当前角色为follower");
                ArrayList<String> commands = new ArrayList<>();
                commands.add("--helper");
                commands.add(command.getMasterHost() + ":9010");
                commands.add("--daemon");
                
                ServiceRoleRunner startRunner = new ServiceRoleRunner();
                startRunner.setProgram(command.getStartRunner().getProgram());
                startRunner.setArgs(commands);
                startRunner.setTimeout("600");
                startResult = serviceHandler.start(startRunner, command.getStatusRunner(),
                        command, command.getRunAs());
                if (startResult.getExecResult()) {
                    // add follower
                    try {
                        String rootPassword = command.getVariables()
                                .getOrDefault("${DORIS.root_password}", "");
                        MasterCallbackClient callbackClient = MasterCallbackClient.getInstance();
                        if (callbackClient != null) {
                            callbackClient.registerOlapNode(
                                    command.getMasterHost(), NetUtil.getLocalhostStr(),
                                    OlapNodeType.ADD_FE_FOLLOWER, rootPassword);
                        } else {
                            logger.warn("MasterCallbackClient not initialized, skipping FE follower registration");
                        }
                        logger.info("slave fe start success");
                    } catch (Exception e) {
                        logger.error("add slave fe failed {}", ThrowableUtils.getStackTrace(e));
                    }
                } else {
                    logger.error("slave fe start failed");
                }
            } else {
                logger.info("第一次启动FE, 当前角色为master");
                startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(), command, command.getRunAs());
                // fe leader安装成功,初始化登录账号
                if (startResult.getExecResult()) {
                    String password = OlapUtils.getUniPassword(feUniConfPath);
                    passwordInit(workPath, HostUtils.getLocalHostName(), password);
                }
            }
        } else {
            startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(), command, command.getRunAs());
        }
        return startResult;
    }
    
    private void passwordInit(String workPath, String masterHost, String password) {
        String feConfPath = workPath + Constants.SLASH + "fe" + Constants.SLASH + "conf" + Constants.SLASH + "fe.conf";
        Props props = Props.getProp(feConfPath, Charset.defaultCharset());
        String queryPort = props.getProperty("query_port");
        String url = String.format("jdbc:mysql://%s:%s", masterHost, queryPort);
        Connection con = null;
        try {
            logger.info("doris init password,url:{}", url);
            con = DbUtil.use(new SimpleDataSource(url, "root", "")).getConnection();
            // Jdbc root密码
            SqlExecutor.execute(con, String.format("SET PASSWORD = PASSWORD('%s')", password));
            // WebUI admin密码
            SqlExecutor.execute(con, String.format("SET PASSWORD FOR 'admin'@'%%' = PASSWORD('%s')", password));
            logger.info("doris init password finished");
        } catch (Exception e) {
            logger.error(e.getMessage() + ",doris init password fail. 可能密码已初始化过", e);
        } finally {
            DbUtil.close(con);
        }
    }
}
