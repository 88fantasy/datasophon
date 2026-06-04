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

import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * K8s 服务命令执行记录
 *
 * @author zhanghuangbin
 * @date 2026-03-30
 */
public interface ClusterK8sServiceCommandService extends IService<ClusterK8sServiceCommandEntity> {
    
    ClusterK8sServiceCommandEntity getCommandById(String commandId);
    
    /**
     * 分页查询 K8s 服务命令
     *
     * @param clusterId 集群 ID
     * @param serviceName 服务名称
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    IPage<ClusterK8sServiceCommandEntity> findCommandByPage(Integer clusterId, String serviceName, Integer page, Integer pageSize);
    
}
