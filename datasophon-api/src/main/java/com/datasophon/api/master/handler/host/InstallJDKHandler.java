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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Session;

public class InstallJDKHandler implements DispatcherWorkerHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(InstallJDKHandler.class);
    
    @Override
    public boolean handle(Session session, HostInfo hostInfo) {
        hostInfo.setProgress(60);
        ArchType arch = MinaUtils.getArch(session);
        hostInfo.setMessage("开始安装JDK");
        // JDK8：大数据组件（HDFS/YARN/DolphinScheduler 等）运行时依赖；
        // JDK17：部分 K8s 相关组件按需使用；
        // JDK21：Datasophon Manager 平台自身运行时依赖，Worker 进程本身即编译为
        // JDK21 字节码，缺失时 StartWorkerHandler 启动的 Worker 进程无法运行。
        return installJdk(session, hostInfo, arch, Constants.X86JDK, Constants.ARMJDK,
                Constants.JDK8_EXTRACT_DIR_NAME, Constants.JDK8_HOME_ALIAS)
                && installJdk(session, hostInfo, arch, Constants.JDK17_X86, Constants.JDK17_ARM,
                        Constants.JDK17_EXTRACT_DIR_NAME, Constants.JDK17_HOME_ALIAS)
                && installJdk(session, hostInfo, arch, Constants.JDK21_X86, Constants.JDK21_ARM,
                        Constants.JDK21_EXTRACT_DIR_NAME, Constants.JDK21_HOME_ALIAS);
    }
    
    private boolean installJdk(Session session, HostInfo hostInfo, ArchType arch, String x86Pkg, String armPkg,
                               String extractDirName, String homeAlias) {
        boolean exists = MinaUtils.execCmd(session, "test -d " + homeAlias).isSuccess();
        if (exists) {
            return true;
        }
        
        String pkg = null;
        if (ArchType.X86_64 == arch) {
            pkg = x86Pkg;
        } else if (ArchType.AARCH64 == arch) {
            pkg = armPkg;
        }
        if (pkg == null) {
            hostInfo.setMessage(String.format("安装jdk失败，未找到适配架构%s的jdk安装包", arch));
            return false;
        }
        
        MinaUtils.uploadFile(session, Constants.INSTALL_PATH, Constants.MASTER_MANAGE_PACKAGE_PATH + Constants.SLASH + pkg);
        MinaUtils.execCmdWithResult(session,
                String.format("tar -zxvf %s/%s -C %s/", Constants.INSTALL_PATH, pkg, Constants.INSTALL_PATH));
        // 解压到与其他组件同处的安装根目录后，软链到版本无关的固定别名，与 tar 包内版本号解耦
        MinaUtils.execCmdWithResult(session, String.format("rm -rf %s && ln -s %s/%s %s",
                homeAlias, Constants.INSTALL_PATH, extractDirName, homeAlias));
        return true;
    }
}
