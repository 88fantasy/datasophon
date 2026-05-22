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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TransportProperties 单元测试（纯 POJO，无 Spring 上下文）。
 *
 * <p>验证 pekko / grpc / both 三种模式下 isPekkoEnabled / isGrpcEnabled 的正确性。</p>
 */
class TransportPropertiesTest {

    @Test
    @DisplayName("默认值为 pekko")
    void defaultTransport_isPekko() {
        TransportProperties tp = new TransportProperties();
        assertThat(tp.getTransport()).isEqualTo("pekko");
        assertThat(tp.isPekkoEnabled()).isTrue();
        assertThat(tp.isGrpcEnabled()).isFalse();
    }

    @ParameterizedTest(name = "transport={0} → pekko={1}, grpc={2}")
    @CsvSource({
            "pekko, true,  false",
            "grpc,  false, true",
            "both,  true,  true"
    })
    @DisplayName("三种传输模式的开关组合")
    void transportModes_enableCorrectFlags(String transport,
                                           boolean expectPekko,
                                           boolean expectGrpc) {
        TransportProperties tp = new TransportProperties();
        tp.setTransport(transport);

        assertThat(tp.isPekkoEnabled())
                .as("transport=%s isPekkoEnabled", transport)
                .isEqualTo(expectPekko);
        assertThat(tp.isGrpcEnabled())
                .as("transport=%s isGrpcEnabled", transport)
                .isEqualTo(expectGrpc);
    }
}
