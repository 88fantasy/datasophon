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


package com.datasophon.api.master.handler.service;

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class ServiceStatusHandler extends ServiceHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceStatusHandler.class);

    private boolean quickCheck = false;
    @Override
    public ExecResult handlerRequest(ServiceRoleInfo serviceRoleInfo) throws Exception {
        logger.info("start to check service status {} in {}", serviceRoleInfo.getName(), serviceRoleInfo.getHostname());
        // 启动
        Map<String, String> globalVariables = GlobalVariables.getVariables(serviceRoleInfo.getClusterId());
        ServiceRoleOperateCommand cmd = new ServiceRoleOperateCommand();
        cmd.setServiceName(serviceRoleInfo.getParentName());
        cmd.setPackageName(resolvePackageName(serviceRoleInfo));
        cmd.setServiceRoleName(serviceRoleInfo.getName());
        cmd.setStartRunner(serviceRoleInfo.getStartRunner());
        cmd.setDecompressPackageName(serviceRoleInfo.getDecompressPackageName());
        cmd.setCreateDecompressDir(serviceRoleInfo.getCreateDecompressDir());
        cmd.setStatusRunner(serviceRoleInfo.getStatusRunner());
        cmd.setSlave(serviceRoleInfo.isSlave());
        cmd.setCommandType(CommandType.CHECK_STATUS);
        cmd.setMasterHost(serviceRoleInfo.getMasterHost());
        cmd.setManagerHost(CacheUtils.getString(Constants.HOSTNAME));
        cmd.setVariables(GlobalVariables.getVariables(serviceRoleInfo.getClusterId()));

        if (quickCheck) {
            cmd.setTimes(3);
        }
        
        logger.info("service master host is {}", serviceRoleInfo.getMasterHost());
        
        cmd.setEnableRangerPlugin(serviceRoleInfo.getEnableRangerPlugin());
        cmd.setRunAs(serviceRoleInfo.getRunAs());
        Boolean enableKerberos = Boolean.parseBoolean(globalVariables.get("${enable" + serviceRoleInfo.getParentName() + "Kerberos}"));
        logger.info("{} enable kerberos is {}", serviceRoleInfo.getParentName(), enableKerberos);
        cmd.setEnableKerberos(enableKerberos);
        if (serviceRoleInfo.getRoleType() == ServiceRoleType.CLIENT) {
            return invokeNext(serviceRoleInfo, ExecResult.success());
        } else {
            WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
            ExecResult statusResult = adapter.serviceRoleStatus(serviceRoleInfo.getHostname(), cmd);
            return invokeNext(serviceRoleInfo, statusResult);
        }
    }
}
