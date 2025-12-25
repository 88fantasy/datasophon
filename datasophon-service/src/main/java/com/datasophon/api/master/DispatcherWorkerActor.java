/*
 *
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
 *
 */

package com.datasophon.api.master;

import akka.actor.UntypedActor;
import cn.hutool.core.util.ObjectUtil;
import com.datasophon.api.master.handler.host.CheckWorkerMd5Handler;
import com.datasophon.api.master.handler.host.DecompressWorkerHandler;
import com.datasophon.api.master.handler.host.DispatcherWorkerHandlerChain;
import com.datasophon.api.master.handler.host.InstallJDKHandler;
import com.datasophon.api.master.handler.host.StartWorkerHandler;
import com.datasophon.api.master.handler.host.UploadWorkerHandler;
import com.datasophon.api.utils.CommonUtils;
import com.datasophon.api.utils.MessageResolverUtils;
import com.datasophon.api.utils.MinaUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.command.DispatcherHostAgentCommand;
import com.datasophon.common.enums.InstallState;
import com.datasophon.common.enums.SSHAuthType;
import com.datasophon.common.model.HostInfo;
import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.PackageStorageUtils;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.HostUtils;
import com.datasophon.common.utils.JschUtils;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

public class DispatcherWorkerActor extends UntypedActor {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherWorkerActor.class);

    @Override
    public void preRestart(Throwable reason, Option<Object> message) throws Exception {
        logger.info("host actor restart because {}", reason.getMessage());
        super.preRestart(reason, message);
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        DispatcherHostAgentCommand command = (DispatcherHostAgentCommand) message;
        HostInfo hostInfo = command.getHostInfo();
        String localIp = HostUtils.getLocalIp();
        String localHostName = HostUtils.getLocalHostName();
        logger.info("start dispatcher host agent :{}", hostInfo.getHostname());
        hostInfo.setMessage(
                MessageResolverUtils.getMessage(
                        "distributed.host.management.agent.installation.package"));
        Session session = JschUtils.getJSchSession(SSHAuthType.AUTO, hostInfo.getHostname(), hostInfo.getSshPort(), hostInfo.getSshUser(), hostInfo.getSshPassword());

        DispatcherWorkerHandlerChain handlerChain = new DispatcherWorkerHandlerChain();
        if (localIp.equals(hostInfo.getIp())) {
            ExecResult result = null;
            Exception ex = null;
            try {
                PackageStorage packageStorage = PackageStorageUtils.getStorage();
                packageStorage.downloadPackageToLocal(Constants.WORKER_PACKAGE_NAME);
                result = MinaUtils.execCmd(session, Constants.UNZIP_DDH_WORKER_CMD);
            } catch (Exception e) {
                logger.error(e.getMessage());
                ex = e;
            }
            if (result != null && result.isSuccess()) {
                logger.info("distribution  datasophon-worker.tar.gz success");
                logger.info("md5.verification datasophon-worker.tar.gz success");
                logger.info("decompress datasophon-worker.tar.gz success");
                hostInfo.setProgress(50);
                hostInfo.setMessage(MessageResolverUtils
                        .getMessage("installation.package.decompressed.success.and.modify.configuration.file"));
            } else {
                ex = ex == null ? new RuntimeException(String.format("dispatch worker fail, cmd result %s", result.getExecErrOut())) : ex;
                logger.error("dispatcher manage node host agent failed", ex);
                hostInfo.setErrMsg(ex.getMessage());
                hostInfo.setMessage(MessageResolverUtils
                        .getMessage("dispatcher manage node host agent failed"));
                CommonUtils.updateInstallState(InstallState.FAILED, hostInfo);
                throw ex;
            }
        } else {
            handlerChain.addHandler(new UploadWorkerHandler());
            handlerChain.addHandler(new CheckWorkerMd5Handler());
            handlerChain.addHandler(new DecompressWorkerHandler());
        }

        handlerChain.addHandler(new InstallJDKHandler());
        handlerChain.addHandler(
                new StartWorkerHandler(command.getClusterId(), command.getClusterFrame()));
        handlerChain.handle(session, hostInfo);
        if (ObjectUtil.isNotEmpty(session)) {
            session.disconnect();
        }
    }

}
