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

package com.datasophon.api.master.handler.host;

import com.datasophon.api.utils.MinaUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.model.HostInfo;
import com.jcraft.jsch.Session;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstallJDKHandler implements DispatcherWorkerHandler {

    private static final Logger logger = LoggerFactory.getLogger(InstallJDKHandler.class);

    @Override
    public boolean handle(Session session, HostInfo hostInfo) {
        hostInfo.setProgress(60);
        ArchType arch = MinaUtils.getArch(session);
        String testResult = MinaUtils.execCmdWithResult(session, "test -d /usr/local/jdk1.8.0_333");
        boolean exists = !StringUtils.isNotBlank(testResult) || !"failed".equals(testResult);
        if (!exists) {
            String pkg = null;
            if (ArchType.X86_64 == arch) {
                pkg = Constants.X86JDK;
            } else  if (ArchType.AARCH64 == arch) {
                pkg = Constants.ARMJDK;
            }
            if (pkg == null) {
                hostInfo.setMessage(String.format("安装jdk失败，未找到适配架构%s的jdk安装包", arch));
                return false;
            }

            hostInfo.setMessage("开始安装JDK");
            MinaUtils.uploadFile(session, "/usr/local", Constants.MASTER_MANAGE_PACKAGE_PATH + Constants.SLASH + pkg);
            MinaUtils.execCmdWithResult(session, String.format("tar -zxvf /usr/local/%s -C /usr/local/", pkg));
        }

        return true;
    }
}
