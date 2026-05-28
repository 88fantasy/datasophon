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

import com.datasophon.api.enums.Status;
import com.datasophon.api.utils.MinaUtils;
import com.datasophon.common.model.CheckResult;
import com.datasophon.common.model.HostInfo;
import com.datasophon.common.utils.HostUtils;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 主机 SSH 连通性检测 Spring Service，业务逻辑来自 {@link HostConnectActor}。
 */
@Service
public class HostConnectService {

    private static final Logger logger = LoggerFactory.getLogger(HostConnectService.class);

    /**
     * 异步检测主机 SSH 连通性（替代 HostConnectActor.tell(command)）。
     * 结果写入传入的 HostInfo 对象（调用方从缓存 Map 中读取）。
     */
    @Async("masterExecutor")
    public void checkHostConnectivity(HostInfo hostInfo) {
        String localIp = HostUtils.getLocalIp();
        String localHostName = HostUtils.getLocalHostName();
        logger.info("datasophon manager install hostname and ip: {}, {}", localHostName, localIp);
        logger.info("start host check: {}", hostInfo.getHostname());

        if (hostInfo.getIp().equals(localIp)) {
            logger.info("datasophon manager node doesn't need to be checked");
            hostInfo.setCheckResult(new CheckResult(
                    Status.CHECK_HOST_SUCCESS.getCode(), Status.CHECK_HOST_SUCCESS.getMsg()));
        } else {
            Session session = null;
            Status status = Status.CONNECTION_FAILED;
            try {
                session = MinaUtils.openConnection(
                        hostInfo.getHostname(), hostInfo.getSshPort(),
                        hostInfo.getSshUser(), hostInfo.getSshPassword());
                if (session != null) {
                    status = Status.CHECK_HOST_SUCCESS;
                }
            } catch (Exception e) {
                logger.warn("connect {}@{}:{} fail, {}",
                        hostInfo.getSshUser(), hostInfo.getHostname(),
                        hostInfo.getSshPort(), e.getMessage());
            } finally {
                MinaUtils.closeConnection(session);
            }
            hostInfo.setCheckResult(new CheckResult(status.getCode(), status.getMsg()));
        }
        logger.info("end host check: {}", hostInfo.getHostname());
    }
}
