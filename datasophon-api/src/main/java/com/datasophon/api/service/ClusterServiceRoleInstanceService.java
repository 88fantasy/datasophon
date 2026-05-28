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

import com.baomidou.mybatisplus.extension.service.IService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.util.List;

/**
 * 集群服务角色实例表
 *
 * @author gaodayu
 * @email gaodayu2022@163.com
 * @date 2022-04-24 16:25:17
 */
public interface ClusterServiceRoleInstanceService extends IService<ClusterServiceRoleInstanceEntity> {

  List<ClusterServiceRoleInstanceEntity> listStoppedServiceRoleListByHostnameAndClusterId(String hostname,
                                                                                          Integer clusterId);

  List<ClusterServiceRoleInstanceEntity> getServiceRoleListByHostnameAndClusterId(String hostname, Integer clusterId);

  ClusterServiceRoleInstanceEntity getOneServiceRole(String serviceRoleName, String hostname, Integer clusterId);

  Result listAll(Integer serviceInstanceId, String hostname, Integer serviceRoleState, String serviceRoleName,
                 Integer roleGroupId, Integer page, Integer pageSize);

  Result getLog(Integer serviceRoleInstanceId) throws Exception;

  List<ClusterServiceRoleInstanceEntity> getServiceRoleInstanceListByServiceId(int id);

  List<ClusterServiceRoleInstanceEntity> getServiceRoleInstanceListByClusterId(int clusterId);

  Result deleteServiceRole(List<String> idList);

  List<ClusterServiceRoleInstanceEntity> getServiceRoleInstanceListByClusterIdAndRoleName(Integer clusterId,
                                                                                          String roleName);

  Result restartObsoleteService(Integer roleGroupId);

  Result decommissionNode(String serviceRoleInstanceIds, String serviceName) throws Exception;

  void updateToNeedRestart(Integer roleGroupId);

  void updateToNeedRestartByHost(String hostName);


  List<ClusterServiceRoleInstanceEntity> listRoleIns(String hostname, String serviceName);

}
