package com.datasophon.common.k8s.spec.yaml;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML 合并工具类
 * @author zhanghuangbin
 */
public class YamlMergeUtils {

    /**
     * 合并两个 YAML 格式的字符串
     * @param originalYaml 第一个 YAML 字符串（可能包含多文档，用 --- 分隔）
     * @param deltaYaml 第二个 YAML 字符串（单个文档）
     * @return 合并后的 YAML 字符串（yaml1 中每个文档都与 yaml2 合并）
     */
    public static String mergeContent(String originalYaml, String deltaYaml) {
        Yaml yaml = new Yaml();

        // 解析 yaml1 的所有文档
        Iterable<Object> docs1 = yaml.loadAll(originalYaml);

        // 解析 yaml2
        Object doc2 = yaml.load(deltaYaml);

        // 对 yaml1 中每个文档都与 yaml2 合并
        List<Object> mergedDocs = new ArrayList<>();
        for (Object doc1 : docs1) {
            if (doc1 == null) {
                continue;
            }
            if (doc1 instanceof Map && doc2 instanceof Map) {
                mergedDocs.add(deepMerge((Map<String, Object>) doc1, (Map<String, Object>) doc2));
            } else {
                // 如果不是 Map，用 yaml2 覆盖
                mergedDocs.add(doc2);
            }
        }

        return dumpYamlAll(mergedDocs);
    }

    /**
     * 深度合并两个 Map
     * @param map1 第一个 Map
     * @param map2 第二个 Map
     * @return 合并后的 Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMerge(Map<String, Object> map1, Map<String, Object> map2) {
        Map<String, Object> result = new HashMap<>(map1);

        for (Map.Entry<String, Object> entry : map2.entrySet()) {
            String key = entry.getKey();
            Object value2 = entry.getValue();
            Object value1 = result.get(key);

            if (value1 == null) {
                result.put(key, value2);
            } else if (value1 instanceof Map && value2 instanceof Map) {
                result.put(key, deepMerge((Map<String, Object>) value1, (Map<String, Object>) value2));
            } else if (value1 instanceof List && value2 instanceof List) {
                List<Object> mergedList = new ArrayList<>((List<Object>) value1);
                mergedList.addAll((List<Object>) value2);
                result.put(key, mergedList);
            } else {
                // 如果类型不匹配或不是 Map/List，后者覆盖前者
                result.put(key, value2);
            }
        }

        return result;
    }

    /**
     * 将多个文档转回 YAML 字符串（用 --- 分隔）
     * @param documents 文档列表
     * @return YAML 字符串
     */
    private static String dumpYamlAll(List<Object> documents) {
        Yaml yaml = new Yaml();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < documents.size(); i++) {
            if (i > 0) {
                sb.append("---\n");
            }
            Object doc = documents.get(i);
            if (doc instanceof Map) {
                sb.append(yaml.dump(doc));
            } else {
                sb.append(yaml.dump(doc));
            }
        }

        return sb.toString();
    }
}
