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

package com.datasophon.common.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 传输层辅助类：将 {@code Map<Generators, List<ServiceConfig>>} 转换为
 * {@code List<ConfigFileEntry>} 以规避 JSON 序列化时对象 key 的限制。
 *
 * <p>仅用于 gRPC 传输层，不涉及业务逻辑。</p>
 */
@Data
public class ConfigFileEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private Generators generators;
    private List<ServiceConfig> configs;

    public static List<ConfigFileEntry> fromMap(Map<Generators, List<ServiceConfig>> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }
        return map.entrySet().stream().map(e -> {
            ConfigFileEntry entry = new ConfigFileEntry();
            entry.setGenerators(e.getKey());
            entry.setConfigs(e.getValue());
            return entry;
        }).collect(Collectors.toList());
    }

    public static Map<Generators, List<ServiceConfig>> toMap(List<ConfigFileEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<Generators, List<ServiceConfig>> map = new LinkedHashMap<>();
        entries.forEach(e -> {
            if (e.getGenerators() != null) {
                map.put(e.getGenerators(), e.getConfigs());
            }
        });
        return map;
    }
}
