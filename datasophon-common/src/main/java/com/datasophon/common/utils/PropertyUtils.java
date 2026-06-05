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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.util.StrUtil;

/**
 * property utils
 * single instance
 */
public class PropertyUtils {
    
    // api 进程默认配置文件；worker 进程通过 -DconfigFileName=conf/worker.properties 覆盖
    public static final String CONFIG_HOME = "conf/api.properties";
    
    private static final String FUNCTIONAL_FILE_PATH;
    /**
     * logger
     */
    private static final Logger logger = LoggerFactory.getLogger(PropertyUtils.class);
    
    private static final Properties properties = new Properties();
    
    private PropertyUtils() {
        throw new UnsupportedOperationException("Construct PropertyUtils");
    }
    
    static {
        List<String> propertyFiles = new ArrayList<>();
        // configFileName 系统属性允许每个进程选择各自的配置文件（api.properties / worker.properties）。
        // 未指定时回落 CONFIG_HOME（"conf/common.properties"），保持向后兼容。
        String configFileName = System.getProperty("configFileName", CONFIG_HOME);
        propertyFiles.add(FileUtils.concatPath(System.getenv("DDH_HOME"), configFileName));
        
        String path = System.getProperty("commonPropertiesLocation");
        if (StrUtil.isNotBlank(path)) {
            propertyFiles.add(path);
        }
        
        String usedFileName = null;
        for (String fileName : propertyFiles) {
            File file = new File(fileName);
            InputStream inputStream = null;
            try {
                inputStream = Files.newInputStream(file.toPath());
                properties.load(inputStream);
                usedFileName = fileName;
            } catch (FileNotFoundException | NoSuchFileException e) {
                logger.warn("file {} do not exist, we just ignore", fileName);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                System.exit(1);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }
        FUNCTIONAL_FILE_PATH = usedFileName;
        if (usedFileName == null) {
            logger.error("can not load config file from {}", StrUtil.join(",", propertyFiles));
            System.exit(1);
        } else {
            logger.info("used {} file as the functional properties", usedFileName);
        }
    }
    
    public static File getFunctionalPropertyFile() {
        return new File(FUNCTIONAL_FILE_PATH);
    }
    /**
     * get property value
     *
     * @param key property name
     * @return property value
     */
    public static String getString(String key) {
        return properties.getProperty(key.trim());
    }
    
    /**
     * get property value
     *
     * @param key        property name
     * @param defaultVal default value
     * @return property value
     */
    public static String getString(String key, String defaultVal) {
        String val = properties.getProperty(key.trim());
        return val == null ? defaultVal : val;
    }
    
    /**
     * get property value
     *
     * @param key property name
     * @return get property int value , if key == null, then return -1
     */
    public static int getInt(String key) {
        return getInt(key, -1);
    }
    
    /**
     * @param key          key
     * @param defaultValue default value
     * @return property value
     */
    public static int getInt(String key, int defaultValue) {
        String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.info(e.getMessage(), e);
        }
        return defaultValue;
    }
    
    /**
     * get property value
     *
     * @param key property name
     * @return property value
     */
    public static boolean getBoolean(String key) {
        String value = properties.getProperty(key.trim());
        if (null != value) {
            return Boolean.parseBoolean(value);
        }
        
        return false;
    }
    
    /**
     * get property value
     *
     * @param key          property name
     * @param defaultValue default value
     * @return property value
     */
    public static Boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key.trim());
        if (null != value) {
            return Boolean.parseBoolean(value);
        }
        
        return defaultValue;
    }
    
    /**
     * get property long value
     *
     * @param key        key
     * @param defaultVal default value
     * @return property value
     */
    public static long getLong(String key, long defaultVal) {
        String val = getString(key);
        return val == null ? defaultVal : Long.parseLong(val);
    }
    
    /**
     * @param key key
     * @return property value
     */
    public static long getLong(String key) {
        return getLong(key, -1);
    }
    
    /**
     * get all properties with specified prefix, like: fs.
     *
     * @param prefix prefix to search
     * @return all properties with specified prefix
     */
    public static Map<String, String> getPrefixedProperties(String prefix) {
        Map<String, String> matchedProperties = new HashMap<>();
        for (String propName : properties.stringPropertyNames()) {
            if (propName.startsWith(prefix)) {
                matchedProperties.put(propName, properties.getProperty(propName));
            }
        }
        return matchedProperties;
    }
    
    /**
     *
     */
    public static void setValue(String key, String value) {
        properties.setProperty(key, value);
    }
    
}
