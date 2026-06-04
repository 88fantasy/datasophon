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

import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.grpc.api.GrpcConstants;
import com.datasophon.grpc.api.HeartbeatRequest;
import com.datasophon.grpc.api.HeartbeatResponse;
import com.datasophon.grpc.api.RegisterRequest;
import com.datasophon.grpc.api.RegisterResponse;
import com.datasophon.grpc.api.UnregisterRequest;
import com.datasophon.grpc.api.WorkerRegistryServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Worker 端 gRPC 客户端：向 Master 注册节点并维持心跳。
 *
 * <p>由 {@link com.datasophon.worker.WorkerApplicationServer} 在 main() 中初始化。</p>
 *
 * <ul>
 *   <li>启动时调用 {@link #register()} 注册自身</li>
 *   <li>每 {@value #HEARTBEAT_INTERVAL_SECONDS}s 发送一次心跳</li>
 *   <li>关闭时调用 {@link #close()} 注销并释放 Channel</li>
 * </ul>
 */
public class MasterRegistryClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MasterRegistryClient.class);

    // 端口与心跳常量统一从 GrpcConstants 读取，避免两端各自硬编码

    private final ManagedChannel channel;
    private final WorkerRegistryServiceGrpc.WorkerRegistryServiceBlockingStub stub;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;

    private final String hostname;
    private final String ip;
    private final String masterHost;
    private final String cpuArchitecture;
    private final int clusterId;

    /**
     * 构造 MasterRegistryClient。
     *
     * @param masterHost      Master 可达地址（IP 或 hostname，从 PropertyUtils 读取 Constants.MASTER_HOST）
     * @param hostname         本机主机名（作为注册表唯一标识，不变）
     * @param ip               本机可达 IP，Master 用于 gRPC 回拨（空时由 Master 回落 hostname 解析）
     * @param cpuArchitecture CPU 架构（x86_64 / aarch64）
     * @param clusterId        所属集群 ID
     */
    public MasterRegistryClient(String masterHost, String hostname, String ip,
                                 String cpuArchitecture, int clusterId) {
        this.masterHost = masterHost;
        this.hostname = hostname;
        this.ip = ip;
        this.cpuArchitecture = cpuArchitecture;
        this.clusterId = clusterId;

        this.channel = ManagedChannelBuilder
                .forAddress(masterHost, GrpcConstants.MASTER_GRPC_PORT)
                .usePlaintext()  // Phase 0 明文，与 Pekko 当前安全模型一致
                .build();
        this.stub = WorkerRegistryServiceGrpc.newBlockingStub(channel);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "worker-grpc-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 向 Master 注册本节点，注册成功后启动心跳定时器。
     */
    public void register() {
        RegisterRequest req = RegisterRequest.newBuilder()
                .setHostname(hostname)
                .setGrpcPort(GrpcConstants.WORKER_GRPC_PORT)
                .setCpuArchitecture(cpuArchitecture)
                .setClusterId(clusterId)
                .setIp(ip != null ? ip : "")
                .build();
        try {
            RegisterResponse resp = stub
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .register(req);
            if (resp.getSuccess()) {
                log.info("Worker gRPC registered to master {}: hostname={}", masterHost, hostname);
                startHeartbeat();
            } else {
                log.warn("Worker gRPC registration failed: {}", resp.getMessage());
            }
        } catch (StatusRuntimeException e) {
            log.warn("Worker gRPC register to master {} failed, will retry via heartbeat: {}",
                    masterHost, e.getStatus());
            // 注册失败也启动心跳定时器：heartbeat 收到 success=false 时会重试 register()。
            // startHeartbeat() 已保证幂等（取消旧任务后再调度），此处安全。
            startHeartbeat();
        }
    }

    /** 主动注销并取消心跳。 */
    public void unregister() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        try {
            stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                    .unregister(UnregisterRequest.newBuilder().setHostname(hostname).build());
            log.info("Worker gRPC unregistered from master: hostname={}", hostname);
        } catch (StatusRuntimeException e) {
            log.warn("Worker gRPC unregister failed: {}", e.getStatus());
        }
    }

    @Override
    public void close() {
        unregister();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private void startHeartbeat() {
        // 先取消旧任务，保证同一时刻只有一个心跳定时器在运行（H2 修复）
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleWithFixedDelay(
                this::sendHeartbeat,
                GrpcConstants.HEARTBEAT_INTERVAL_SECONDS,
                GrpcConstants.HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        try {
            HeartbeatResponse resp = stub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .heartbeat(HeartbeatRequest.newBuilder().setHostname(hostname).build());
            if (!resp.getSuccess()) {
                // master 不认识本节点，重新注册
                log.warn("Heartbeat rejected by master (worker unknown), re-registering...");
                register();
            }
        } catch (StatusRuntimeException e) {
            log.warn("Heartbeat to master {} failed: {}", masterHost, e.getStatus());
        }
    }
}
