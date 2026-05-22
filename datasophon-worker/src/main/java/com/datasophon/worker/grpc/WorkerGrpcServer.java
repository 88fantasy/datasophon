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

package com.datasophon.worker.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Worker 端 gRPC 服务器（Phase 1）。
 *
 * <p>监听端口 {@value #PORT}，注册 {@link WorkerCommandGrpcService}。
 * 由 {@link com.datasophon.worker.WorkerApplicationServer} 在 {@code transport=grpc|both}
 * 时启动。</p>
 */
public class WorkerGrpcServer {

    private static final Logger log = LoggerFactory.getLogger(WorkerGrpcServer.class);

    /** Worker gRPC 服务端口（须与 MasterRegistryClient.WORKER_GRPC_PORT 保持一致） */
    static final int PORT = 18082;

    private final Server server;

    public WorkerGrpcServer() {
        this.server = ServerBuilder.forPort(PORT)
                .addService(new WorkerCommandGrpcService())
                .build();
    }

    /** 启动服务器，阻塞直到端口绑定完成。 */
    public void start() throws IOException {
        server.start();
        log.info("Worker gRPC server started on port {}", PORT);
    }

    /** 优雅关闭：等待 5s，超时后强制终止。 */
    public void stop() {
        if (server == null || server.isShutdown()) {
            return;
        }
        server.shutdown();
        try {
            if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.shutdownNow();
        }
        log.info("Worker gRPC server stopped");
    }
}
