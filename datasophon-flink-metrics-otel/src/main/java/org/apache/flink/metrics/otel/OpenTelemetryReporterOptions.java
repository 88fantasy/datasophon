/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.flink.metrics.otel;

import java.time.Duration;
import java.util.function.Consumer;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.flink.metrics.MetricConfig;
import org.apache.flink.util.TimeUtils;

/** Supported configuration for the OpenTelemetry reporter. */
final class OpenTelemetryReporterOptions {

    static final ConfigOption<String> EXPORTER_ENDPOINT =
            ConfigOptions.key("exporter.endpoint").stringType().noDefaultValue();
    static final ConfigOption<String> EXPORTER_TIMEOUT =
            ConfigOptions.key("exporter.timeout").stringType().noDefaultValue();
    static final ConfigOption<String> SERVICE_NAME =
            ConfigOptions.key("service.name").stringType().noDefaultValue();
    static final ConfigOption<String> SERVICE_VERSION =
            ConfigOptions.key("service.version").stringType().noDefaultValue();

    private OpenTelemetryReporterOptions() {}

    static void configureEndpoint(MetricConfig config, Consumer<String> consumer) {
        String key = EXPORTER_ENDPOINT.key();
        if (!config.containsKey(key)) {
            throw new IllegalArgumentException("Must set " + key);
        }
        consumer.accept(config.getProperty(key));
    }

    static void configureTimeout(MetricConfig config, Consumer<Duration> consumer) {
        String key = EXPORTER_TIMEOUT.key();
        if (config.containsKey(key)) {
            consumer.accept(TimeUtils.parseDuration(config.getProperty(key)));
        }
    }
}
