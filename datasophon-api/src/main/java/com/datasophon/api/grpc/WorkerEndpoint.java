/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datasophon.api.grpc;

import lombok.Data;

import java.time.Instant;

/**
 * Worker 节点的 gRPC 连接端点信息。
 * 由 WorkerRegistry 维护，在 MasterRegistryClient 注册时写入。
 */
@Data
public class WorkerEndpoint {

    /** Worker 主机名（唯一标识） */
    private final String hostname;

    /** Worker gRPC server 端口（默认 18082） */
    private final int grpcPort;

    /** CPU 架构（x86_64 / aarch64） */
    private final String cpuArchitecture;

    /** 所属集群 ID */
    private final int clusterId;

    /** 最近一次心跳时间（注册时初始化，心跳时更新） */
    private volatile Instant lastHeartbeat;

    public WorkerEndpoint(String hostname, int grpcPort, String cpuArchitecture, int clusterId) {
        this.hostname = hostname;
        this.grpcPort = grpcPort;
        this.cpuArchitecture = cpuArchitecture;
        this.clusterId = clusterId;
        this.lastHeartbeat = Instant.now();
    }

    /** 更新心跳时间戳 */
    public void touch() {
        this.lastHeartbeat = Instant.now();
    }
}
