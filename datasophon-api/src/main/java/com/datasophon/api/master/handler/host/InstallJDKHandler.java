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
