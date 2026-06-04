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
import com.datasophon.dao.entity.FrameServiceRoleEntity;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 框架服务角色表
 *
 * @author gaodayu
 * @email gaodayu2022@163.com
 * @date 2022-04-18 14:38:53
 */
public interface FrameServiceRoleService extends IService<FrameServiceRoleEntity> {
    
    List<FrameServiceRoleEntity> getServiceRoleList(Integer clusterId, List<Integer> serviceIds, Integer serviceRoleType);
    
    FrameServiceRoleEntity getServiceRoleByServiceIdAndServiceRoleName(Integer id, String name);
    
    FrameServiceRoleEntity getServiceRoleByFrameCodeAndServiceRoleName(String clusterFrame, String serviceRoleName);
    
    Result getNonMasterRoleList(Integer clusterId, String serviceIds);
    
    Result getServiceRoleByServiceName(Integer clusterId, String serviceName);
    
    List<FrameServiceRoleEntity> getAllServiceRoleList(Integer frameServiceId);
    
    String getServiceName(String frameCode, String serviceRoleName);
}
