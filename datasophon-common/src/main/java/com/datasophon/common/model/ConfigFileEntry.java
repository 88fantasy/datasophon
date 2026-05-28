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
