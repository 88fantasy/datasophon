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
import com.datasophon.api.utils.MessageResolverUtils;
import com.datasophon.api.utils.MinaUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.InstallState;
import com.datasophon.common.model.HostInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.JschUtils;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecompressWorkerHandler implements DispatcherWorkerHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(DecompressWorkerHandler.class);
    
    @Override
    public boolean handle(Session session, HostInfo hostInfo) {
        ExecResult result = JschUtils.ensureRemotePathExists(session, Constants.INSTALL_PATH);
        if (result.isSuccess()) {
            result = MinaUtils.execCmd(session, Constants.UNZIP_DDH_WORKER_CMD);
        }
        if (!result.isSuccess()) {
            logger.error("tar -zxvf datasophon-worker.tar.gz failed, {}", result.getExecResult());
            hostInfo.setErrMsg("tar -zxvf datasophon-worker.tar.gz failed." + result.getExecResult());
            hostInfo.setMessage("解压安装包失败");
            CommonUtils.updateInstallState(InstallState.FAILED, hostInfo);
            return false;
        }
        logger.info("decompress datasophon-worker.tar.gz success");
        hostInfo.setProgress(50);
        hostInfo.setMessage("解压安装包成功");
        return true;
    }
}
