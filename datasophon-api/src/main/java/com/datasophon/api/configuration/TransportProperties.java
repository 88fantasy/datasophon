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

package com.datasophon.api.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Master-Worker 传输协议开关。
 *
 * <pre>
 * datasophon:
 *   transport: pekko   # pekko | grpc | both
 * </pre>
 *
 * <ul>
 *   <li>{@code pekko} — 全走 Pekko（默认，迁移完成前保持兼容）</li>
 *   <li>{@code grpc}  — 全走 gRPC（迁移完成后切换）</li>
 *   <li>{@code both}  — 两路并行，gRPC 优先，Pekko 作为 fallback（迁移过渡期）</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "datasophon")
public class TransportProperties {

    /**
     * Master-Worker 通信传输协议。取值：pekko | grpc | both。
     * 默认 pekko，保持向后兼容。
     */
    private String transport = "pekko";

    public boolean isPekkoEnabled() {
        return "pekko".equals(transport) || "both".equals(transport);
    }

    public boolean isGrpcEnabled() {
        return "grpc".equals(transport) || "both".equals(transport);
    }
}
