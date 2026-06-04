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

import com.datasophon.api.load.Application;
import com.datasophon.api.utils.CommonUtils;
import com.datasophon.api.utils.MinaUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.InstallState;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.model.HostInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.JschUtils;
import com.datasophon.common.utils.OsUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Session;

public class StartWorkerHandler implements DispatcherWorkerHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(StartWorkerHandler.class);
    
    private Integer clusterId;
    
    private String clusterFrame;
    
    public StartWorkerHandler(Integer clusterId, String clusterFrame) {
        this.clusterId = clusterId;
        this.clusterFrame = clusterFrame;
    }
    
    @Override
    public boolean handle(Session session, HostInfo hostInfo) throws UnknownHostException {
        String installPath = Constants.INSTALL_PATH;
        String properties = "/conf/common.properties";
        String masterCommonProperties = Constants.MASTER_INSTALL_HOME + properties;
        String workerCommonProperties = installPath + "/datasophon-worker" + properties;
        
        ExecResult result = ExecResult.error("修改配置文件失败");
        try (FileInputStream fis = new FileInputStream(masterCommonProperties)) {
            result = JschUtils.sendInputStream(session, fis, workerCommonProperties, 5, true);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        
        if (!result.isSuccess()) {
            logger.error("common.properties update failed, {}", result.getErrorTraceMessage());
            hostInfo.setErrMsg("common.properties update failed");
            hostInfo.setMessage("覆盖配置文件失败");
            CommonUtils.updateInstallState(InstallState.FAILED, hostInfo);
            return false;
        }
        
        String serverPort = Application.getProperty("server.port", "8081");
        String localHostName = InetAddress.getLocalHost().getHostName();
        String updateCommonPropertiesResult = MinaUtils.execCmdWithResult(session,
                Constants.UPDATE_COMMON_CMD +
                        localHostName +
                        Constants.SPACE +
                        serverPort +
                        Constants.SPACE +
                        this.clusterFrame +
                        Constants.SPACE +
                        this.clusterId +
                        Constants.SPACE +
                        Constants.INSTALL_PATH);
        if (StringUtils.isBlank(updateCommonPropertiesResult) || "failed".equals(updateCommonPropertiesResult)) {
            logger.error("common.properties update failed");
            hostInfo.setErrMsg("common.properties update failed");
            hostInfo.setMessage("修改配置文件失败");
            CommonUtils.updateInstallState(InstallState.FAILED, hostInfo);
            return false;
        }
        
        // osType
        String osStr = MinaUtils.execCmdWithResult(session, Constants.OS_VERSION_CMD);
        OsType osType = OsUtils.getOs(osStr);
        String addServiceCmd = "chkconfig --add datasophon-worker";
        if (OsType.isUnbuntu(osType)) {
            addServiceCmd = "update-rc.d datasophon-worker defaults 90";
        }
        
        // Initialize environment
        MinaUtils.execCmdWithResult(session, "ulimit -n 102400");
        MinaUtils.execCmdWithResult(session, "sysctl -w vm.max_map_count=2000000");
        // Set startup and self start
        MinaUtils.execCmdWithResult(session,
                "\\cp " + installPath + "/datasophon-worker/script/datasophon-worker /etc/rc.d/init.d/");
        MinaUtils.execCmdWithResult(session, "chmod +x /etc/rc.d/init.d/datasophon-worker");
        
        MinaUtils.execCmdWithResult(session, addServiceCmd);
        MinaUtils.execCmdWithResult(session,
                "\\cp " + installPath + "/datasophon-worker/script/datasophon-env.sh /etc/profile.d/");
        MinaUtils.execCmdWithResult(session, ". /etc/profile.d/datasophon-env.sh");
        hostInfo.setMessage("开始启动worker");
        MinaUtils.execCmdWithResult(session, "service datasophon-worker restart");
        hostInfo.setProgress(75);
        hostInfo.setCreateTime(new Date());
        
        logger.info("end dispatcher host agent :{}", hostInfo.getHostname());
        return true;
    }
}
