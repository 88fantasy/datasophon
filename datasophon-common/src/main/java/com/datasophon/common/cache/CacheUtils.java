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


package com.datasophon.common.cache;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;

import java.util.function.Function;

/**
 * Cache工具类
 */
public class CacheUtils {
    
    private static final Cache<String, Object> cache = CacheUtil.newLRUCache(4096);
    
    public static Object get(String key) {
        return cache.get(key);
    }

    public static Object get(Namespace namespace) {
        return cache.get(namespace.toString());
    }


    public static void put(String key, Object value) {
        cache.put(key, value);
    }
    
    public static boolean containsKey(String key) {
        return cache.containsKey(key);
    }
    
    public static void removeKey(String key) {
        cache.remove(key);
    }
    
    public static Integer getInteger(String key) {
        Object data = cache.get(key);
        return (Integer) data;
    }
    
    public static Boolean getBoolean(String key) {
        Object data = cache.get(key);
        return (Boolean) data;
    }
    
    public static String getString(String key) {
        Object data = cache.get(key);
        return (String) data;
    }


    /**
     * 线程不安全，但是勉强够用
     * @param key
     * @param function
     * @return
     * @param <T>
     */
    public static <T> T computeIfAbsent(String key, Function<String, T> function) {
        if (containsKey(key)) {
            return (T) get(key);
        }else {
            final T t = function.apply(key);
            cache.put(key, t);
            return t;
        }
    }
}
