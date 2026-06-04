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

package com.datasophon.api.grpc;

import com.datasophon.api.master.service.WorkerStartService;
import com.datasophon.common.model.StartWorkerMessage;
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
    private final WorkerStartService workerStartService;
    
    public WorkerRegistryGrpcService(WorkerRegistry workerRegistry,
                                     WorkerStartService workerStartService) {
        this.workerRegistry = workerRegistry;
        this.workerStartService = workerStartService;
    }
    
    @Override
    public void register(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
        WorkerEndpoint endpoint = new WorkerEndpoint(
                request.getHostname(),
                request.getIp(),
                request.getGrpcPort(),
                request.getCpuArchitecture(),
                request.getClusterId());
        workerRegistry.register(endpoint);
        
        // 触发 Worker 首次注册处理（Prometheus 配置更新、服务自动启动等）
        StartWorkerMessage msg = new StartWorkerMessage();
        msg.setHostname(request.getHostname());
        msg.setClusterId(request.getClusterId());
        msg.setCpuArchitecture(request.getCpuArchitecture());
        workerStartService.handleWorkerRegistration(msg);
        
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
