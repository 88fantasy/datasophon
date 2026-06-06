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

package com.datasophon.api.service.cmd;

import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostCommandEntity;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 集群服务操作指令主机指令表
 *
 * @author gaodayu
 */
public interface ClusterServiceCommandHostCommandService extends IService<ClusterServiceCommandHostCommandEntity> {
    
    Result getHostCommandList(String hostname, String commandHostId, Integer page, Integer pageSize);
    
    List<ClusterServiceCommandHostCommandEntity> getHostCommandListByCommandId(String id);
    
    ClusterServiceCommandHostCommandEntity getByHostCommandId(String hostCommandId);
    
    void updateByHostCommandId(ClusterServiceCommandHostCommandEntity hostCommand);
    
    Long getHostCommandSizeByHostnameAndCommandHostId(String hostname, String commandHostId);
    
    Integer getHostCommandTotalProgressByHostnameAndCommandHostId(String hostname, String commandHostId);
    
    Result getHostCommandLog(Integer clusterId, String hostCommandId) throws Exception;
    
    List<ClusterServiceCommandHostCommandEntity> findFailedHostCommand(String hostname, String commandHostId);
    
    List<ClusterServiceCommandHostCommandEntity> findCanceledHostCommand(String hostname, String commandHostId);
}
