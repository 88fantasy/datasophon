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
