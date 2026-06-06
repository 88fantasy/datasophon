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

package com.datasophon.api.service.host;

import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterHostDO;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

public interface ClusterHostService extends IService<ClusterHostDO> {
    
    ClusterHostDO getClusterHostByHostname(String hostname);
    
    ClusterHostDO getClusterHostByIp(String ip);
    
    Result listByPage(Integer clusterId, String hostname, String ip, String cpuArchitecture, Integer hostState,
                      String orderField, String orderType, Integer page, Integer pageSize);
    
    List<ClusterHostDO> getHostListByClusterId(Integer id);
    
    Result getRoleListByHostname(Integer clusterId, String hostname);
    
    /**
     * 批量删除主机。
     * 删除主机，首先停止主机上的服务
     * 其次删除主机 worker，同时移除 Prometheus hosts
     * 然后删除主机运行的实例
     *
     */
    Result deleteHosts(String hostIds);
    
    Result getRack(Integer clusterId);
    
    void removeHostByClusterId(Integer id);
    
    void updateBatchNodeLabel(List<String> hostIds, String nodeLabel);
    
    List<ClusterHostDO> getHostListByIds(List<String> ids);
    
    Result assignRack(Integer clusterId, String rack, String hostIds);
    
}
