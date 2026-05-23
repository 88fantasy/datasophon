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

package com.datasophon.worker.grpc;

import com.datasophon.grpc.api.MasterCallbackServiceGrpc;
import com.datasophon.grpc.api.OlapNodeType;
import com.datasophon.grpc.api.OlapRegistrationRequest;
import com.datasophon.grpc.api.OlapRegistrationResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Worker 端 → Master 反向回调 gRPC 客户端（静态 Holder）。
 *
 * <p>Worker 是纯 Java 进程，不使用 Spring 注入，通过静态持有单例供策略类调用。
 * 由 {@link com.datasophon.worker.WorkerApplicationServer} 在 main() 中初始化。</p>
 *
 * <p>复用与 {@link MasterRegistryClient} 相同的 Master 端口（18081），
 * 独立 Channel 以便单独关闭。</p>
 */
public class MasterCallbackClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MasterCallbackClient.class);

    /** Master gRPC server 端口（与 MasterRegistryClient 一致） */
    private static final int MASTER_GRPC_PORT = 18081;

    /** 静态单例，由 init() 设置，策略类通过 getInstance() 获取 */
    private static MasterCallbackClient instance;

    private final ManagedChannel channel;
    private final MasterCallbackServiceGrpc.MasterCallbackServiceBlockingStub stub;

    private MasterCallbackClient(String masterHost) {
        this.channel = ManagedChannelBuilder
                .forAddress(masterHost, MASTER_GRPC_PORT)
                .usePlaintext()
                .build();
        this.stub = MasterCallbackServiceGrpc.newBlockingStub(channel);
    }

    /**
     * 在 Worker 启动时初始化单例。必须在策略类使用前调用。
     *
     * @param masterHost Master 主机名
     */
    public static void init(String masterHost) {
        instance = new MasterCallbackClient(masterHost);
        log.info("MasterCallbackClient initialized, master={}", masterHost);
    }

    /**
     * 获取单例。若未初始化则返回 null（调用方应降级到本地 OlapUtils）。
     */
    public static MasterCallbackClient getInstance() {
        return instance;
    }

    /**
     * 通知 Master 将本节点注册到 OLAP 集群。
     *
     * @param feMaster     FE Leader 主机名
     * @param hostname     本节点主机名
     * @param nodeType     节点类型（BE / FE Follower / FE Observer）
     * @param rootPassword Doris root 密码
     * @return true=通知成功（Master 异步执行注册）；false=通信失败
     */
    public boolean registerOlapNode(String feMaster, String hostname,
                                    OlapNodeType nodeType, String rootPassword) {
        OlapRegistrationRequest req = OlapRegistrationRequest.newBuilder()
                .setFeMaster(feMaster)
                .setHostname(hostname)
                .setNodeType(nodeType)
                .setRootPassword(rootPassword)
                .build();
        try {
            OlapRegistrationResponse resp = stub
                    .withDeadlineAfter(30, TimeUnit.SECONDS)
                    .registerOlapNode(req);
            if (resp.getSuccess()) {
                log.info("OLAP node registration dispatched to master: type={}, hostname={}", nodeType, hostname);
            } else {
                log.warn("Master rejected OLAP registration: type={}, hostname={}, msg={}", nodeType, hostname, resp.getMessage());
            }
            return resp.getSuccess();
        } catch (StatusRuntimeException e) {
            log.warn("MasterCallback.registerOlapNode failed: type={}, hostname={}, status={}",
                    nodeType, hostname, e.getStatus());
            return false;
        }
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
