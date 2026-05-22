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

import com.datasophon.grpc.api.HeartbeatRequest;
import com.datasophon.grpc.api.HeartbeatResponse;
import com.datasophon.grpc.api.RegisterRequest;
import com.datasophon.grpc.api.RegisterResponse;
import com.datasophon.grpc.api.UnregisterRequest;
import com.datasophon.grpc.api.UnregisterResponse;
import com.datasophon.grpc.api.WorkerRegistryServiceGrpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Master 端 gRPC 服务：接收 Worker 的注册、心跳、注销请求。
 * <p>
 * 由 {@code @GrpcService} 自动注册到内嵌 Netty gRPC Server（端口 18081）。
 * </p>
 */
@GrpcService
public class WorkerRegistryGrpcService extends WorkerRegistryServiceGrpc.WorkerRegistryServiceImplBase {

    private final WorkerRegistry workerRegistry;

    public WorkerRegistryGrpcService(WorkerRegistry workerRegistry) {
        this.workerRegistry = workerRegistry;
    }

    @Override
    public void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
        WorkerEndpoint endpoint = new WorkerEndpoint(
                request.getHostname(),
                request.getGrpcPort(),
                request.getCpuArchitecture(),
                request.getClusterId());
        workerRegistry.register(endpoint);

        responseObserver.onNext(RegisterResponse.newBuilder()
                .setSuccess(true)
                .setMessage("registered")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        boolean ok = workerRegistry.heartbeat(request.getHostname());
        responseObserver.onNext(HeartbeatResponse.newBuilder().setSuccess(ok).build());
        responseObserver.onCompleted();
    }

    @Override
    public void unregister(UnregisterRequest request, StreamObserver<UnregisterResponse> responseObserver) {
        workerRegistry.unregister(request.getHostname());
        responseObserver.onNext(UnregisterResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }
}
