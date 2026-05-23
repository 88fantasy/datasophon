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

package com.datasophon.grpc.api;

/**
 * 跨模块 gRPC 常量（端口 + 心跳）。
 *
 * <p>Master（datasophon-api）与 Worker（datasophon-worker）共同依赖此模块，
 * 在此集中定义端口与心跳参数，避免两端各自硬编码导致隐性耦合。</p>
 *
 * <ul>
 *   <li>{@link #MASTER_GRPC_PORT} — Master 端 gRPC 服务监听端口</li>
 *   <li>{@link #WORKER_GRPC_PORT} — Worker 端 gRPC 服务监听端口</li>
 *   <li>{@link #HEARTBEAT_INTERVAL_SECONDS} — Worker 心跳发送间隔</li>
 *   <li>{@link #HEARTBEAT_TIMEOUT_SECONDS}  — Master 判定 Worker 离线的超时阈值（= 间隔 × 3）</li>
 * </ul>
 */
public final class GrpcConstants {

    /** Master gRPC server 默认端口（WorkerRegistryService + MasterCallbackService）。 */
    public static final int MASTER_GRPC_PORT = 18081;

    /** Worker gRPC server 默认端口（WorkerCommandService）。 */
    public static final int WORKER_GRPC_PORT = 18082;

    /**
     * Worker 心跳发送间隔（秒）。
     * Worker 侧 {@code MasterRegistryClient} 的 {@code scheduleWithFixedDelay} 使用此值。
     */
    public static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    /**
     * Master 侧心跳超时阈值（秒）= {@link #HEARTBEAT_INTERVAL_SECONDS} × 3。
     * 连续 3 次心跳缺失后，{@code WorkerRegistry} 将该 Worker 标记为离线。
     */
    public static final int HEARTBEAT_TIMEOUT_SECONDS = HEARTBEAT_INTERVAL_SECONDS * 3;

    private GrpcConstants() {
        // 工具类，禁止实例化
    }
}
