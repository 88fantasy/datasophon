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

package com.datasophon.api.service;

import com.datasophon.common.utils.Result;

public interface HostInstallService {
    
    Result getInstallStep(Integer type);
    
    Result analysisHostList(Integer clusterId, String hosts, String sshUser, String sshPass, Integer sshPort, Integer page,
                            Integer pageSize);
    
    Result getHostCheckStatus(Integer clusterId, String sshUser, Integer sshPort);
    
    Result rehostCheck(Integer clusterId, String hostnames, String sshUser, Integer sshPort);
    
    Result dispatcherHostAgentList(Integer id, Integer installStateCode, Integer page, Integer clusterId);
    
    Result reStartDispatcherHostAgent(Integer clusterId, String hostnames);
    
    Result hostCheckCompleted(Integer clusterId);
    
    Result cancelDispatcherHostAgent(Integer clusterId, String hostname, Integer installStateCode);
    
    Result dispatcherHostAgentCompleted(Integer clusterId);
    
    Result generateHostAgentCommand(String clusterHostIds, String commandType) throws Exception;
    
    /**
     * 启动/停止 主机上安装的服务启动
     * @throws Exception
     */
    Result generateHostServiceCommand(String clusterHostIds, String commandType) throws Exception;
}
