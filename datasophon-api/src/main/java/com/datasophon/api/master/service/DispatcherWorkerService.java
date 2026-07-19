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

package com.datasophon.api.master.service;

import com.datasophon.api.master.handler.host.CheckWorkerMd5Handler;
import com.datasophon.api.master.handler.host.DecompressWorkerHandler;
import com.datasophon.api.master.handler.host.DispatcherWorkerHandlerChain;
import com.datasophon.api.master.handler.host.InstallJDKHandler;
import com.datasophon.api.master.handler.host.StartWorkerHandler;
import com.datasophon.api.master.handler.host.UploadWorkerHandler;
import com.datasophon.api.utils.CommonUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.command.DispatcherHostAgentCommand;
import com.datasophon.common.enums.InstallState;
import com.datasophon.common.enums.SSHAuthType;
import com.datasophon.common.model.HostInfo;
import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.utils.HostUtils;
import com.datasophon.common.utils.JschUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.jcraft.jsch.Session;

/**
 * Worker Agent 分发安装 Spring Service，业务逻辑来自 {@link DispatcherWorkerActor}。
 */
@Service
public class DispatcherWorkerService {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherWorkerService.class);

    /**
     * 异步分发并安装 Worker Agent（替代 DispatcherWorkerActor.tell(command)）。
     */
    @Async("masterExecutor")
    public void dispatchWorkerAgent(DispatcherHostAgentCommand command) {
        HostInfo hostInfo = command.getHostInfo();
        String localIp = HostUtils.getLocalIp();
        logger.info("start dispatcher host agent: {}, ip: {}", hostInfo.getHostname(), hostInfo.getIp());
        hostInfo.setMessage("开始分发worker agent安装包");

        Session session = null;
        try {
            PackageStorage packageStorage = StorageUtils.getPackageStorage();
            packageStorage.downloadPackageToLocal(Constants.WORKER_PACKAGE_NAME);

            DispatcherWorkerHandlerChain handlerChain = new DispatcherWorkerHandlerChain();
            if (!localIp.equals(hostInfo.getIp())) {
                handlerChain.addHandler(new UploadWorkerHandler());
                handlerChain.addHandler(new CheckWorkerMd5Handler());
            }
            handlerChain.addHandler(new DecompressWorkerHandler());
            handlerChain.addHandler(new InstallJDKHandler());
            handlerChain.addHandler(new StartWorkerHandler(command.getClusterId()));

            Integer port = hostInfo.getSshPort() == null ? 22 : hostInfo.getSshPort();
            session = JschUtils.getJSchSession(SSHAuthType.AUTO,
                    hostInfo.getIp(), port,
                    hostInfo.getSshUser(), hostInfo.getSshPassword());
            handlerChain.handle(session, hostInfo);
        } catch (Throwable e) {
            logger.error("dispatcher manage node host agent {} failed", hostInfo.getHostname(), e);
            hostInfo.setErrMsg("安装worker失败，原因：" + e.getMessage());
            hostInfo.setMessage("安装worker失败");
            CommonUtils.updateInstallState(InstallState.FAILED, hostInfo);
        } finally {
            if (session != null) {
                try {
                    session.disconnect();
                } catch (Exception ignored) {
                    // ignore disconnect error
                }
            }
        }
    }
}
