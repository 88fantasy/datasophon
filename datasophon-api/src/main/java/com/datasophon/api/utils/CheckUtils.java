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

package com.datasophon.api.utils;

import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import cn.hutool.core.util.StrUtil;
import com.datasophon.api.configuration.TransportProperties;
import com.datasophon.api.enums.Status;
import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.load.ServiceInfoMap;
import com.datasophon.api.load.ServiceRoleMap;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.command.ExecuteCmdCommand;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.AlertLevel;
import org.apache.commons.lang3.StringUtils;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CheckUtils {
    
    private CheckUtils() {
        throw new IllegalStateException("CheckUtils class");
    }
    
    /**
     * check username
     *
     * @param userName user name
     * @return true if user name regex valid,otherwise return false
     */
    public static boolean checkUserName(String userName) {
        return regexChecks(userName, Constants.REGEX_USER_NAME);
    }
    
    /**
     * check email
     *
     * @param email email
     * @return true if email regex valid, otherwise return false
     */
    public static boolean checkEmail(String email) {
        if (StringUtils.isEmpty(email)) {
            return false;
        }
        
        return email.length() > 5 && email.length() <= 40 && regexChecks(email, Constants.REGEX_MAIL_NAME);
    }
    
    /**
     * check project description
     *
     * @param desc desc
     * @return true if description regex valid, otherwise return false
     */
    public static Map<String, Object> checkDesc(String desc) {
        Map<String, Object> result = new HashMap<>(16);
        if (StringUtils.isNotEmpty(desc) && desc.length() > Constants.TWO_HUNDRRD) {
            result.put(Constants.STATUS, Status.REQUEST_PARAMS_NOT_VALID_ERROR);
            result.put(Constants.MSG,
                    MessageFormat.format(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getMsg(), "desc length"));
        } else {
            result.put(Constants.STATUS, Status.SUCCESS);
        }
        return result;
    }
    
    /**
     * check password
     *
     * @param password password
     * @return true if password regex valid, otherwise return false
     */
    public static boolean checkPassword(String password) {
        return StringUtils.isNotEmpty(password) && password.length() >= 2 && password.length() <= 20;
    }
    
    /**
     * check phone
     * phone can be empty.
     *
     * @param phone phone
     * @return true if phone regex valid, otherwise return false
     */
    public static boolean checkPhone(String phone) {
        return StringUtils.isEmpty(phone) || phone.length() == 11;
    }
    
    /**
     * check params
     *
     * @param userName user name
     * @param password password
     * @param email    email
     * @param phone    phone
     * @return true if user parameters are valid, other return false
     */
    public static boolean checkUserParams(String userName, String password, String email, String phone) {
        return CheckUtils.checkUserName(userName) &&
                CheckUtils.checkEmail(email) &&
                CheckUtils.checkPassword(password) &&
                CheckUtils.checkPhone(phone);
    }
    
    /**
     * regex check
     *
     * @param str     input string
     * @param pattern regex pattern
     * @return true if regex pattern is right, otherwise return false
     */
    private static boolean regexChecks(String str, Pattern pattern) {
        if (StringUtils.isEmpty(str)) {
            return false;
        }
        
        return pattern.matcher(str).matches();
    }

    /**
     * statusRunner检测状态
     * @param roleInstanceEntity
     * @param map
     */
    public static void handlerServiceRoleStatusRunnerCheck(ClusterServiceRoleInstanceEntity roleInstanceEntity,
                                        Map<String, ClusterServiceRoleInstanceEntity> map) {
        Integer clusterId = roleInstanceEntity.getClusterId();

        ClusterInfoEntity cluster = ProcessUtils.getClusterInfo(clusterId);
        String frameCode = cluster.getClusterFrame();

        String key = frameCode + Constants.UNDERLINE + roleInstanceEntity.getServiceName() + Constants.UNDERLINE
                + roleInstanceEntity.getServiceRoleName();
        ServiceRoleInfo serviceRoleInfo = ServiceRoleMap.get(key);
        ServiceInfo serviceInfo =
                ServiceInfoMap.get(frameCode + Constants.UNDERLINE + roleInstanceEntity.getServiceName());

        if(serviceRoleInfo.getStatusRunner() == null
                || StrUtil.isBlank(serviceRoleInfo.getStatusRunner().getProgram())){
            //不写则不执行检测命令
            return;
        }

        String linkDirName = PkgInstallPathUtils.getLinkDirName(serviceRoleInfo);
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add(linkDirName + Constants.SLASH + serviceRoleInfo.getStatusRunner().getProgram());
        commandList.addAll(serviceRoleInfo.getStatusRunner().getArgs());
        try {
            ExecResult execResult;
            TransportProperties tp = SpringTool.getApplicationContext().getBean(TransportProperties.class);
            if (tp.isGrpcEnabled()) {
                WorkerCommandClient workerCommandClient =
                        SpringTool.getApplicationContext().getBean(WorkerCommandClient.class);
                execResult = workerCommandClient.executeCmd(roleInstanceEntity.getHostname(), commandList);
            } else {
                ActorSelection execCmdActor = ActorUtils.actorSystem.actorSelection(
                        "pekko://datasophon@" + roleInstanceEntity.getHostname() + ":2552/user/worker/executeCmdActor");
                ExecuteCmdCommand cmdCommand = new ExecuteCmdCommand();
                cmdCommand.setCommands(commandList);
                Timeout timeout = new Timeout(Duration.create(30, TimeUnit.SECONDS));
                Future<Object> execFuture = Patterns.ask(execCmdActor, cmdCommand, timeout);
                execResult = (ExecResult) Await.result(execFuture, timeout.duration());
            }
            if (execResult.getExecResult()) {
                ProcessUtils.recoverAlert(roleInstanceEntity);
            } else {
                String alertTargetName = roleInstanceEntity.getServiceRoleName() + " Survive";
                ProcessUtils.saveAlert(roleInstanceEntity, alertTargetName, AlertLevel.EXCEPTION, "restart");
            }
        } catch (Exception e) {
            // save alert
            String alertTargetName = roleInstanceEntity.getServiceRoleName() + " Survive";
            ProcessUtils.saveAlert(roleInstanceEntity, alertTargetName, AlertLevel.EXCEPTION, "restart");
        }
    }
}
