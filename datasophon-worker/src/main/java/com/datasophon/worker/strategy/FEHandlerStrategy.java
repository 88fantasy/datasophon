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

import akka.actor.ActorRef;
import cn.hutool.core.net.NetUtil;
import cn.hutool.db.DbUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.sql.SqlExecutor;
import cn.hutool.json.JSONUtil;
import cn.hutool.setting.dialect.Props;
import com.datasophon.common.Constants;
import com.datasophon.common.command.OlapOpsType;
import com.datasophon.common.command.OlapSqlExecCommand;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.ServiceRoleRunner;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.HostUtils;
import com.datasophon.common.utils.OlapUtils;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ThrowableUtils;
import com.datasophon.worker.handler.ServiceHandler;
import com.datasophon.worker.utils.ActorUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class FEHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public FEHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ExecResult startResult = new ExecResult();
        logger.info("FEHandlerStrategy start fe" + JSONUtil.toJsonStr(command));
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        String workPath = PkgInstallPathUtils.getInstallHome(command);
        String feUniConfPath = workPath + "/fe/conf/fe.uni.conf";
        if (command.getCommandType() == CommandType.INSTALL_SERVICE) {
            if (command.isSlave()) {
                logger.info("first start  fe");
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
                        OlapSqlExecCommand sqlExecCommand = new OlapSqlExecCommand();
                        sqlExecCommand.setVariables(command.getVariables());
                        sqlExecCommand.setFeMaster(command.getMasterHost());
                        sqlExecCommand.setHostName(NetUtil.getLocalhostStr());
                        sqlExecCommand.setOpsType(OlapOpsType.ADD_FE_FOLLOWER);
                        sqlExecCommand.setWorkerPath(workPath);
                        ActorUtils.getRemoteActor(command.getManagerHost(), "masterNodeProcessingActor")
                                .tell(sqlExecCommand, ActorRef.noSender());
                        logger.info("slave fe start success");
                    } catch (Exception e) {
                        logger.error("add slave fe failed {}", ThrowableUtils.getStackTrace(e));
                    }
                } else {
                    logger.error("slave fe start failed");
                }
            } else {
                startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(), command, command.getRunAs());
                // fe leader安装成功,初始化登录账号
                if (startResult.getExecResult()){
                    String password = OlapUtils.getUniPassword(feUniConfPath);
                    passwordInit(workPath, HostUtils.getLocalHostName(), password);
                }
            }
        } else {
            startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(), command, command.getRunAs());
        }
        return startResult;
    }

    private void passwordInit(String workPath, String masterHost, String password){
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
