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

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.apache.flink.metrics.MetricConfig;

/** Shared OpenTelemetry state for Flink reporters. */
abstract class OpenTelemetryReporterBase {

    protected Resource resource = Resource.getDefault();
    protected MetricExporter exporter;

    protected void open(MetricConfig config) {
        AttributesBuilder attributes = io.opentelemetry.api.common.Attributes.builder();
        if (config.containsKey(OpenTelemetryReporterOptions.SERVICE_NAME.key())) {
            attributes.put(
                    ResourceAttributes.SERVICE_NAME,
                    config.getProperty(OpenTelemetryReporterOptions.SERVICE_NAME.key()));
        }
        if (config.containsKey(OpenTelemetryReporterOptions.SERVICE_VERSION.key())) {
            attributes.put(
                    ResourceAttributes.SERVICE_VERSION,
                    config.getProperty(OpenTelemetryReporterOptions.SERVICE_VERSION.key()));
        }
        resource = resource.merge(Resource.create(attributes.build()));
    }
}
