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

import com.datasophon.api.master.service.MasterNodeProcessingService;
import com.datasophon.common.command.OlapOpsType;
import com.datasophon.common.command.OlapSqlExecCommand;
import com.datasophon.grpc.api.MasterCallbackServiceGrpc;
import com.datasophon.grpc.api.OlapNodeType;
import com.datasophon.grpc.api.OlapRegistrationRequest;
import com.datasophon.grpc.api.OlapRegistrationResponse;

import io.grpc.stub.StreamObserver;

import java.util.Map;

import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Worker → Master 反向回调 gRPC 服务。
 *
 * <p>Worker 安装 Doris/StarRocks 节点后调用 {@link #registerOlapNode}，
 * Master 立即返回 success，异步通过 {@link MasterNodeProcessingService} 执行注册 SQL。</p>
 */
@GrpcService
public class MasterCallbackGrpcService extends MasterCallbackServiceGrpc.MasterCallbackServiceImplBase {
    
    private final MasterNodeProcessingService processingService;
    
    public MasterCallbackGrpcService(MasterNodeProcessingService processingService) {
        this.processingService = processingService;
    }
    
    @Override
    public void registerOlapNode(OlapRegistrationRequest request,
                                 StreamObserver<OlapRegistrationResponse> responseObserver) {
        OlapSqlExecCommand command = new OlapSqlExecCommand();
        command.setFeMaster(request.getFeMaster());
        command.setHostName(request.getHostname());
        command.setOpsType(toOpsType(request.getNodeType()));
        command.setVariables(Map.of("${DORIS.root_password}", request.getRootPassword()));
        
        // @Async — 立即返回，异步执行注册 SQL（fire-and-forget 语义）
        processingService.processOlapNode(command);
        
        responseObserver.onNext(OlapRegistrationResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }
    
    private OlapOpsType toOpsType(OlapNodeType nodeType) {
        switch (nodeType) {
            case ADD_BE:
                return OlapOpsType.ADD_BE;
            case ADD_FE_FOLLOWER:
                return OlapOpsType.ADD_FE_FOLLOWER;
            case ADD_FE_OBSERVER:
                return OlapOpsType.ADD_FE_OBSERVER;
            default:
                throw new IllegalArgumentException("Unknown OlapNodeType: " + nodeType);
        }
    }
}
