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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GenerateAlertConfigCommand.configFileMap} 的 JSON 传输桥接类。
 *
 * <p>{@code HashMap<Generators, List<AlertItem>>} 因 {@link Generators} 不是
 * String 类型，Jackson 无法直接序列化为 JSON Object（key 只能是 String）。
 * 本类将 Map 展平为 List，可安全地做 JSON round-trip。</p>
 *
 * <p>与 {@link ConfigFileEntry} 同模式：
 * <ul>
 *   <li>Master 发送前调 {@link #fromMap(HashMap)} 序列化</li>
 *   <li>Worker 接收后调 {@link #toMap(List)} 还原</li>
 * </ul>
 * </p>
 */
@Data
public class AlertConfigEntry implements Serializable {

    private Generators generators;
    private List<AlertItem> alertItems;

    public static List<AlertConfigEntry> fromMap(HashMap<Generators, List<AlertItem>> map) {
        if (map == null) {
            return null;
        }
        List<AlertConfigEntry> entries = new ArrayList<>(map.size());
        for (Map.Entry<Generators, List<AlertItem>> e : map.entrySet()) {
            AlertConfigEntry entry = new AlertConfigEntry();
            entry.setGenerators(e.getKey());
            entry.setAlertItems(e.getValue());
            entries.add(entry);
        }
        return entries;
    }

    public static HashMap<Generators, List<AlertItem>> toMap(List<AlertConfigEntry> entries) {
        if (entries == null) {
            return new HashMap<>();
        }
        HashMap<Generators, List<AlertItem>> map = new HashMap<>(entries.size());
        for (AlertConfigEntry entry : entries) {
            map.put(entry.getGenerators(), entry.getAlertItems());
        }
        return map;
    }
}
