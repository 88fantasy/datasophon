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

package com.datasophon.api.service.agent;

import com.datasophon.dao.entity.cluster.K8sClusterConfig;

/**
 * K8s Agent 部署服务接口
 */
public interface K8sAgentDeployService {

    /**
     * 部署 Agent 到 K8s 集群
     * @param config K8s 集群配置
     */
    void deployAgent(K8sClusterConfig config);

    /**
     * 从 K8s 集群卸载 Agent
     * @param config K8s 集群配置
     */
    void undeployAgent(K8sClusterConfig config);

    /**
     * 检查 Agent 部署状态
     * @param config K8s 集群配置
     * @return 是否已部署
     */
    boolean checkAgentStatus(K8sClusterConfig config);
}
