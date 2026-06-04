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

package com.datasophon.grpc.api;

/**
 * 跨模块 gRPC 常量（端口 + 心跳）。
 *
 * <p>Master（datasophon-api）与 Worker（datasophon-worker）共同依赖此模块，
 * 在此集中定义端口与心跳参数，避免两端各自硬编码导致隐性耦合。</p>
 *
 * <ul>
 *   <li>{@link #MASTER_GRPC_PORT} — Master 端 gRPC 服务监听端口</li>
 *   <li>{@link #WORKER_GRPC_PORT} — Worker 端 gRPC 服务监听端口</li>
 *   <li>{@link #HEARTBEAT_INTERVAL_SECONDS} — Worker 心跳发送间隔</li>
 *   <li>{@link #HEARTBEAT_TIMEOUT_SECONDS}  — Master 判定 Worker 离线的超时阈值（= 间隔 × 3）</li>
 * </ul>
 */
public final class GrpcConstants {
    
    /** Master gRPC server 默认端口（WorkerRegistryService + MasterCallbackService）。 */
    public static final int MASTER_GRPC_PORT = 18081;
    
    /** Worker gRPC server 默认端口（WorkerCommandService）。 */
    public static final int WORKER_GRPC_PORT = 18082;
    
    /**
     * Worker 心跳发送间隔（秒）。
     * Worker 侧 {@code MasterRegistryClient} 的 {@code scheduleWithFixedDelay} 使用此值。
     */
    public static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    
    /**
     * Master 侧心跳超时阈值（秒）= {@link #HEARTBEAT_INTERVAL_SECONDS} × 3。
     * 连续 3 次心跳缺失后，{@code WorkerRegistry} 将该 Worker 标记为离线。
     */
    public static final int HEARTBEAT_TIMEOUT_SECONDS = HEARTBEAT_INTERVAL_SECONDS * 3;
    
    private GrpcConstants() {
        // 工具类，禁止实例化
    }
}
