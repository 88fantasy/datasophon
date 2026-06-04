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

package com.datasophon.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaceholderUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(PlaceholderUtils.class);
    
    public static String replacePlaceholders(String value, Map<String, String> paramsMap, String regex) {
        // Pattern pattern = Pattern.compile(regex);
        // Matcher matcher = pattern.matcher(value);
        // // 自旋进行最小匹配，直到无法匹配
        // while (matcher.find()) {
        // String group = matcher.group();
        // // 替换匹配内容
        // // logger.info("find match value {}",group);
        // if (paramsMap.containsKey(group)) {
        // value = value.replace(group, paramsMap.get(group));
        // }
        // }
        // return value;
        return replacePlaceholdersRecursive(value, paramsMap, regex);
    }
    
    public static String replacePlaceholdersRecursive(String input, Map<String, String> paramsMap, String regex) {
        Pattern pattern = Pattern.compile(regex);
        int depth = 0;
        
        String result = input;
        while (true) {
            String originalVal = result;
            
            StringBuffer sb = new StringBuffer();
            Matcher matcher = pattern.matcher(result);
            while (matcher.find()) {
                String group = matcher.group();
                String replaceValue = paramsMap.getOrDefault(group, group);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replaceValue));
            }
            matcher.appendTail(sb);
            result = sb.toString();
            
            // 如果没有变化，说明已经替换完毕
            if (originalVal.equals(result)) {
                break;
            }
            
            depth++;
            // 超过10次替换，则认为变量中存在循环引用
            if (depth > 10) {
                throw new IllegalStateException(String.format(
                        "replacing placeholder has reached the max depth 10, is the following string contains a recursive placeholder? \n input value: %s",
                        input));
            }
        }
        return result;
    }
    
    public static List<String> getMatchValue(String value) {
        String regex = "\\[.*?\\]";
        ArrayList<String> list = new ArrayList<>();
        
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(value);
        // 自旋进行最小匹配，直到无法匹配
        while (matcher.find()) {
            String group = matcher.group();
            // 替换匹配内容
            list.add(group);
        }
        return list;
    }
    
    public static List<String> getNewEquipmentNoList(String pre, String last) {
        int length = pre.length();
        ArrayList<String> list = new ArrayList<>();
        Integer start = Integer.parseInt(pre);
        Integer end = Integer.parseInt(last);
        int next = start;
        list.add(pre);
        while (next < end) {
            next = next + 1;
            String nextStr = String.format("%0" + length + "d", next);
            list.add(nextStr);
        }
        return list;
    }
    
}
