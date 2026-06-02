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

import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.enums.HookType;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;

import java.util.Objects;

public class ServiceConfigureHandler extends ServiceHandler {
    
    @Override
    public ExecResult handlerRequest(ServiceRoleInfo serviceRoleInfo) throws Exception {
        // config
        GenerateServiceConfigCommand cmd = new GenerateServiceConfigCommand();
        cmd.setPackageName(resolvePackageName(serviceRoleInfo));
        cmd.setClusterId(serviceRoleInfo.getClusterId());
        cmd.setServiceName(serviceRoleInfo.getParentName());
        cmd.setCofigFileMap(serviceRoleInfo.getConfigFileMap());
        cmd.setDecompressPackageName(serviceRoleInfo.getDecompressPackageName());
        cmd.setCreateDecompressDir(serviceRoleInfo.getCreateDecompressDir());
        cmd.setRunAs(serviceRoleInfo.getRunAs());

        if ("zkserver".equalsIgnoreCase(serviceRoleInfo.getName())) {
            cmd.setMyid((Integer) CacheUtils.get("zkserver_" + serviceRoleInfo.getHostname()));
        }
        cmd.setServiceRoleName(serviceRoleInfo.getName());
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        ExecResult configResult = adapter.configureServiceRole(serviceRoleInfo.getHostname(), cmd);
        if (Objects.nonNull(configResult) && configResult.getExecResult()) {
            if (Objects.nonNull(getNext())) {
                return getNext().handlerRequest(serviceRoleInfo);
            }
        }
        return configResult;
    }
}
