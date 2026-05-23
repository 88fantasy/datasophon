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
