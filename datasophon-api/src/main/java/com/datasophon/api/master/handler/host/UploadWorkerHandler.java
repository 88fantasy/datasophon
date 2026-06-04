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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Session;

public class UploadWorkerHandler implements DispatcherWorkerHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(StartWorkerHandler.class);
    
    @Override
    public boolean handle(Session session, HostInfo hostInfo) {
        boolean uploadFile = MinaUtils.uploadFile(session, Constants.MASTER_MANAGE_PACKAGE_PATH, Constants.MASTER_MANAGE_PACKAGE_PATH + Constants.SLASH + Constants.WORKER_PACKAGE_NAME);
        if (uploadFile) {
            hostInfo.setMessage("同步worker安装包成功");
            hostInfo.setProgress(25);
        } else {
            hostInfo.setMessage("同步worker安装包失败");
            hostInfo.setErrMsg("dispatcher host agent to " + hostInfo.getHostname() + " failed");
            CommonUtils.updateInstallState(InstallState.FAILED, hostInfo);
        }
        return uploadFile;
    }
}
