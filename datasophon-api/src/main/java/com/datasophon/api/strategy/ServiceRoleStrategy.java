/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.api.strategy;

import com.datasophon.api.utils.CheckUtils;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.util.List;
import java.util.Map;

public interface ServiceRoleStrategy {


    /**
     * 处理安装服务在选定主机的后置逻辑
     * 
     * @param clusterId 集群ID，标识要操作的目标集群
     * @param hosts 主机列表，指定要部署或配置服务的主机
     * @param serviceName 服务名称，标识要处理的具体服务
     */
    default void handler(Integer clusterId, List<String> hosts, String serviceName) {
    
    }

    /**
     * 处理安装服务角色的配置信息的后置逻辑
     *
     * @param clusterId 集群ID，标识要操作的目标集群
     * @param list 服务配置列表，包含要配置的服务参数
     * @param serviceName 服务名称，标识要处理的具体服务
     */
    default void handlerConfig(Integer clusterId, List<ServiceConfig> list, String serviceName) {

    }

    /**
     * 获取服务角色的配置信息的拦截器
     *
     * @param clusterId 集群ID，标识要操作的目标集群
     * @param list 服务配置列表，用于存储获取到的服务配置信息
     */
    default void getConfig(Integer clusterId, List<ServiceConfig> list) {

    }

    /**
     * 处理服务角色信息，根据主机名更新服务角色状态
     *
     * @param serviceRoleInfo 服务角色信息，包含要更新的服务角色状态
     * @param hostname 主机名，标识要更新状态的具体主机
     */
    default void handlerServiceRoleInfo(ServiceRoleInfo serviceRoleInfo, String hostname) {

    }


    /**
     * 处理服务角色存活检查
     *
     * @param roleInstanceEntity 角色实例实体，包含要检查的服务角色状态
     * @param map 主机名映射，用于存储主机名与角色实例实体的对应关系
     */
    default void handlerServiceRoleCheck(ClusterServiceRoleInstanceEntity roleInstanceEntity,
                                         Map<String, ClusterServiceRoleInstanceEntity> map) {
        //默认执行检测命令
        CheckUtils.handlerServiceRoleStatusRunnerCheck(roleInstanceEntity, map);
    }
}