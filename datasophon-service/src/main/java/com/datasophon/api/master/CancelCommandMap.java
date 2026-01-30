package com.datasophon.api.master;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CancelCommandMap {
    
    private static final Map<String, String> map = new ConcurrentHashMap<>();
    
    public static void put(String key, String value) {
        map.put(key, value);
    }
    
    public static String get(String key) {
        return map.get(key);
    }
    
    public static boolean exists(String key) {
        return map.containsKey(key);
    }
}
