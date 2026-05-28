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


package com.datasophon.api.master.handler.host;

import com.datasophon.api.utils.CommonUtils;
import com.datasophon.api.utils.MinaUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.InstallState;
import com.datasophon.common.model.HostInfo;
import com.datasophon.common.storage.StorageUtils;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckWorkerMd5Handler implements DispatcherWorkerHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CheckWorkerMd5Handler.class);
    @Override
    public boolean handle(Session session, HostInfo hostInfo) {
        String checkWorkerMd5Result = MinaUtils.execCmdWithResult(session, Constants.CHECK_WORKER_MD5_CMD).trim();
        String md5 = StorageUtils.getPackageStorage().readPackageMd5(Constants.WORKER_PACKAGE_NAME);

        logger.info("{} worker package md5 value is : {}", hostInfo.getHostname(), md5);
        if (!md5.equals(checkWorkerMd5Result)) {
            logger.error("worker package md5 check failed");
            hostInfo.setErrMsg("worker package md5 check failed");
            hostInfo.setMessage("校验安装包MD5失败");
            CommonUtils.updateInstallState(InstallState.FAILED, hostInfo);
            return false;
        }
        hostInfo.setProgress(35);
        hostInfo.setMessage("校验安装包MD5成功，开始解压安装包");
        return true;
    }
}
