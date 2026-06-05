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

package com.datasophon.worker.grpc;

import com.datasophon.grpc.api.GrpcConstants;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker 端 gRPC 服务器（Phase 1）。
 *
 * <p>监听端口 {@value GrpcConstants#WORKER_GRPC_PORT}，注册 {@link WorkerCommandGrpcService}。
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
    
    /** 阻塞主线程直到 gRPC server 关闭（容器/前台运行时调用，防止 JVM 因无非守护线程而提前退出）。 */
    public void awaitTermination() throws InterruptedException {
        server.awaitTermination();
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
