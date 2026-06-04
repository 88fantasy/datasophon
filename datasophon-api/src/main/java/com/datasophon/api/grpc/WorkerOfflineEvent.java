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

import org.springframework.context.ApplicationEvent;

/**
 * Worker 节点离线事件。
 * <p>
 * 由 {@link WorkerRegistry} 在 worker 注销、重新注册（新端口替换旧端点）
 * 或心跳超时时发布。{@link WorkerCommandClient} 监听此事件以关闭并移除
 * 对应 hostname 的缓存 gRPC Channel，防止连接句柄泄漏。
 * </p>
 */
public class WorkerOfflineEvent extends ApplicationEvent {
    
    private final String hostname;
    
    public WorkerOfflineEvent(Object source, String hostname) {
        super(source);
        this.hostname = hostname;
    }
    
    public String getHostname() {
        return hostname;
    }
}
