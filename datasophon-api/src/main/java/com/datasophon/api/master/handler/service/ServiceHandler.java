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

import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.ServicePkgNameUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.model.ArchInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterHostDO;

import lombok.Data;

@Data
public abstract class ServiceHandler {

    private ServiceHandler next;

    public abstract ExecResult handlerRequest(ServiceRoleInfo serviceRoleInfo) throws Exception;


    public ServiceHandler thenNext(ServiceHandler next) {
        this.next = next;
        return next;
    }

    public ExecResult invokeNext(ServiceRoleInfo srvRoleInfo, ExecResult lastResult) throws Exception {
        boolean canGoOn = lastResult != null && lastResult.isSuccess() && next != null;
        if (!canGoOn) {
            return lastResult;
        }
        return next.handlerRequest(srvRoleInfo);
    }

    /**
     * 按主机 CPU 架构从 archInfoMap 中解析当前操作所需的安装包名。
     * 找不到匹配架构时返回 null，调用方应视为失败。
     */
    protected String resolvePackageName(ServiceRoleInfo role) {
        ClusterHostService hostService = SpringTool.getApplicationContext().getBean(ClusterHostService.class);
        ClusterHostDO host = hostService.getClusterHostByHostname(role.getHostname());
        ArchInfo archInfo = ServicePkgNameUtils.getArchInfo(role, host.getCpuArchitecture());
        return archInfo == null ? null : archInfo.getPackageName();
    }
}
