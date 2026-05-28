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

import lombok.Data;

import java.time.Instant;

/**
 * Worker 节点的 gRPC 连接端点信息。
 * 由 WorkerRegistry 维护，在 MasterRegistryClient 注册时写入。
 */
@Data
public class WorkerEndpoint {

    /** Worker 主机名（唯一标识） */
    private final String hostname;

    /** Worker gRPC server 端口（默认 18082） */
    private final int grpcPort;

    /** CPU 架构（x86_64 / aarch64） */
    private final String cpuArchitecture;

    /** 所属集群 ID */
    private final int clusterId;

    /** 最近一次心跳时间（注册时初始化，心跳时更新） */
    private volatile Instant lastHeartbeat;

    public WorkerEndpoint(String hostname, int grpcPort, String cpuArchitecture, int clusterId) {
        this.hostname = hostname;
        this.grpcPort = grpcPort;
        this.cpuArchitecture = cpuArchitecture;
        this.clusterId = clusterId;
        this.lastHeartbeat = Instant.now();
    }

    /** 更新心跳时间戳 */
    public void touch() {
        this.lastHeartbeat = Instant.now();
    }
}
