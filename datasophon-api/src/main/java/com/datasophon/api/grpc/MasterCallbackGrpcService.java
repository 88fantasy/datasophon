/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

import com.datasophon.api.master.service.MasterNodeProcessingService;
import com.datasophon.common.command.OlapOpsType;
import com.datasophon.common.command.OlapSqlExecCommand;
import com.datasophon.grpc.api.MasterCallbackServiceGrpc;
import com.datasophon.grpc.api.OlapNodeType;
import com.datasophon.grpc.api.OlapRegistrationRequest;
import com.datasophon.grpc.api.OlapRegistrationResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Map;

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
            case ADD_BE:          return OlapOpsType.ADD_BE;
            case ADD_FE_FOLLOWER: return OlapOpsType.ADD_FE_FOLLOWER;
            case ADD_FE_OBSERVER: return OlapOpsType.ADD_FE_OBSERVER;
            default: throw new IllegalArgumentException("Unknown OlapNodeType: " + nodeType);
        }
    }
}
