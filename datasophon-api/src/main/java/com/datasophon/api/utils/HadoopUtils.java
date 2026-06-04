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

import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.master.handler.service.ServiceConfigureHandler;
import com.datasophon.common.Constants;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HadoopUtils {
    
    public static ExecResult configQueueProp(ClusterInfoEntity clusterInfo,
                                             HashMap<Generators, List<ServiceConfig>> configFileMap,
                                             ClusterServiceRoleInstanceEntity roleInstanceEntity) throws Exception {
        ServiceRoleInfo serviceRoleInfo = new ServiceRoleInfo();
        serviceRoleInfo.setName("ResourceManager");
        serviceRoleInfo.setParentName("YARN");
        serviceRoleInfo.setConfigFileMap(configFileMap);
        serviceRoleInfo.setDecompressPackageName(PackageUtils.getServiceDcPackageName(clusterInfo.getClusterFrame(), "YARN"));
        serviceRoleInfo.setHostname(roleInstanceEntity.getHostname());
        ServiceConfigureHandler configureHandler = new ServiceConfigureHandler();
        ExecResult execResult = configureHandler.handlerRequest(serviceRoleInfo);
        return execResult;
    }
    
    public static ExecResult refreshQueuePropToYarn(ClusterInfoEntity clusterInfo, String hostname) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add(Constants.INSTALL_PATH + Constants.SLASH
                + PackageUtils.getServiceDcPackageName(clusterInfo.getClusterFrame(), "YARN") + "/bin/yarn");
        commands.add("rmadmin");
        commands.add("-refreshQueues");
        WorkerCommandClient client = SpringTool.getApplicationContext().getBean(WorkerCommandClient.class);
        return client.executeCmd(hostname, commands);
    }
}
