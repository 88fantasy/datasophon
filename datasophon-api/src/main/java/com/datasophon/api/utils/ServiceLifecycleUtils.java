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

package com.datasophon.api.utils;

import com.datasophon.api.master.handler.service.ServiceConfigureHandler;
import com.datasophon.api.master.handler.service.ServiceHandler;
import com.datasophon.api.master.handler.service.ServiceInstallHandler;
import com.datasophon.api.master.handler.service.ServiceStartHandler;
import com.datasophon.api.master.handler.service.ServiceStopHandler;
import com.datasophon.api.master.handler.service.ServiceUpgradeHandler;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.util.List;
import java.util.Map;

/** 服务启停/安装/升级 Handler 链装配(原 ProcessUtils 拆出)。 */
public class ServiceLifecycleUtils {
    
    private ServiceLifecycleUtils() {
    }
    
    public static ExecResult restartService(ServiceRoleInfo serviceRoleInfo, boolean needReConfig) throws Exception {
        ServiceHandler serviceStartHandler = new ServiceStartHandler();
        ServiceHandler serviceStopHandler = new ServiceStopHandler();
        if (needReConfig) {
            ServiceConfigureHandler serviceConfigureHandler = new ServiceConfigureHandler();
            serviceStopHandler.setNext(serviceConfigureHandler);
            serviceConfigureHandler.setNext(serviceStartHandler);
        } else {
            serviceStopHandler.setNext(serviceStartHandler);
        }
        return serviceStopHandler.handlerRequest(serviceRoleInfo);
    }
    
    public static ExecResult startService(ServiceRoleInfo serviceRoleInfo, boolean needReConfig) throws Exception {
        ExecResult execResult;
        if (needReConfig) {
            ServiceConfigureHandler serviceHandler = new ServiceConfigureHandler();
            ServiceHandler serviceStartHandler = new ServiceStartHandler();
            serviceHandler.setNext(serviceStartHandler);
            execResult = serviceHandler.handlerRequest(serviceRoleInfo);
        } else {
            ServiceHandler serviceStartHandler = new ServiceStartHandler();
            execResult = serviceStartHandler.handlerRequest(serviceRoleInfo);
        }
        return execResult;
    }
    
    public static ExecResult stopService(ServiceRoleInfo serviceRoleInfo) throws Exception {
        ServiceHandler serviceStopHandler = new ServiceStopHandler();
        return serviceStopHandler.handlerRequest(serviceRoleInfo);
    }
    
    public static ExecResult startInstallService(ServiceRoleInfo serviceRoleInfo) throws Exception {
        ServiceHandler serviceInstallHandler = new ServiceInstallHandler();
        ServiceHandler serviceConfigureHandler = new ServiceConfigureHandler();
        
        // 安装时，不见是否启动成功(部分软件，需要全部节点启动成功后，状态才能成功)
        ServiceStartHandler serviceStartHandler = new ServiceStartHandler();
        serviceStartHandler.setCheckStatus(false);
        
        serviceInstallHandler.setNext(serviceConfigureHandler);
        serviceConfigureHandler.setNext(serviceStartHandler);
        ExecResult execResult = serviceInstallHandler.handlerRequest(serviceRoleInfo);
        return execResult;
    }
    
    /**
     * 升级角色服务，操作链
     * 1. 停止服务
     * 2. 安装软件
     * 3. 生成配置
     * 4. 启动应用
     */
    public static ExecResult upgradeService(ServiceRoleInfo serviceRoleInfo) throws Exception {
        ServiceHandler handler = new ServiceStopHandler();
        
        handler
                .thenNext(new ServiceUpgradeHandler())
                .thenNext(new ServiceConfigureHandler())
                .thenNext(new ServiceStartHandler());
        
        ExecResult execResult = handler.handlerRequest(serviceRoleInfo);
        return execResult;
    }
    
    public static ExecResult configServiceRoleInstance(ClusterInfoEntity clusterInfo,
                                                       Map<Generators, List<ServiceConfig>> configFileMap,
                                                       ClusterServiceRoleInstanceEntity roleInstanceEntity) throws Exception {
        ServiceRoleInfo serviceRoleInfo = new ServiceRoleInfo();
        serviceRoleInfo.setName(roleInstanceEntity.getServiceRoleName());
        serviceRoleInfo.setParentName(roleInstanceEntity.getServiceName());
        serviceRoleInfo.setConfigFileMap(configFileMap);
        serviceRoleInfo.setHostname(roleInstanceEntity.getHostname());
        ServiceConfigureHandler configureHandler = new ServiceConfigureHandler();
        return configureHandler.handlerRequest(serviceRoleInfo);
    }
}
