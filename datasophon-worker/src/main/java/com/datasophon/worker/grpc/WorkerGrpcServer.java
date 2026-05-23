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

import com.datasophon.grpc.api.GrpcConstants;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
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

    /**
     * gRPC 服务线程池上界：max(8, 2 × CPU 核数)。
     *
     * <p>gRPC-Java 默认使用无界 CachedThreadPool；在高并发（如批量 install）场景下
     * 可能创建数百线程导致堆栈溢出。有界线程池在满负荷时让请求排队而非创建新线程。</p>
     */
    private static final int GRPC_THREAD_POOL_SIZE =
            Math.max(8, Runtime.getRuntime().availableProcessors() * 2);

    private final Server server;

    public WorkerGrpcServer() {
        this.server = ServerBuilder.forPort(GrpcConstants.WORKER_GRPC_PORT)
                .executor(Executors.newFixedThreadPool(GRPC_THREAD_POOL_SIZE, r -> {
                    Thread t = new Thread(r, "worker-grpc-exec");
                    t.setDaemon(true);
                    return t;
                }))
                .addService(new WorkerCommandGrpcService())
                .build();
    }

    /** 启动服务器，阻塞直到端口绑定完成。 */
    public void start() throws IOException {
        server.start();
        log.info("Worker gRPC server started on port {} (threadPool={})",
                GrpcConstants.WORKER_GRPC_PORT, GRPC_THREAD_POOL_SIZE);
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
