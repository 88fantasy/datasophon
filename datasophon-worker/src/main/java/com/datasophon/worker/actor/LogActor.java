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

package com.datasophon.worker.actor;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.command.GetLogCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.worker.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.HashMap;

public class LogActor extends HookTypedActor<GetLogCommand> {
    
    private static final Logger logger = LoggerFactory.getLogger(LogActor.class);

    @Override
    protected void doOnReceive(GetLogCommand command) throws Throwable {
        HashMap<String, String> paramMap = new HashMap<>();
        paramMap.put("${host}", InetAddress.getLocalHost().getHostName());
        String logFileName = PlaceholderUtils.replacePlaceholders(command.getLogFile(), paramMap, Constants.REGEX_VARIABLE);
        logger.info("get log content of {}", logFileName);

        ExecResult execResult = new ExecResult();
        String logStr = "can not find log file";
        if (logFileName.startsWith(StrUtil.SLASH) && FileUtil.exist(logFileName)) {
            logStr = FileUtils.readLastRows(logFileName, Charset.defaultCharset(), PropertyUtils.getInt("rows"));
        } else {
            String path = command.getBaseDir() + Constants.SLASH + Constants.SLASH + logFileName;
            File logFile = new File(path);
            if (logFile.exists()) {
                logStr = FileUtils.readLastRows(path, Charset.defaultCharset(), PropertyUtils.getInt("rows"));
            }
        }
        execResult.setExecResult(true);
        execResult.setExecOut(logStr);
        getSender().tell(execResult, getSelf());
    }
}
