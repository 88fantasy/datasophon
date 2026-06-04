package com.datasophon.common.utils;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.PropertyUtils;

/**
 * @author zhanghuangbin
 */
public class YamlUtils {
    
    public static <T> T parseYaml(String content, Class<T> cls) {
        Constructor constructor = new Constructor(cls, new LoaderOptions());
        org.yaml.snakeyaml.introspector.PropertyUtils propertyUtils = new PropertyUtils();
        propertyUtils.setSkipMissingProperties(true);
        constructor.setPropertyUtils(propertyUtils);
        return new Yaml(constructor).loadAs(content, cls);
    }
}
