package com.datasophon.api.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class AddCommonPropertiesToEnv implements EnvironmentPostProcessor {


    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources propertySources = environment.getPropertySources();
        Properties properties = loadCustomProperties();
        propertySources.addFirst(new PropertiesPropertySource("datasophonConfig", properties));
    }
    
    private Properties loadCustomProperties() {
        Properties properties = new Properties();

        String path = System.getProperty("commonPropertiesLocation");
        File file = new File(path);
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            properties.load(inputStream);
        } catch (Exception e) {
            System.err.println("Failed to load the datasophon configuration (config/datasophon.conf), use application-config.yml");
            return new Properties();
        }
        List<Object> removeKeys = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String val = String.valueOf(entry.getValue()).trim();
            if (StringUtils.isBlank(val)) {
                removeKeys.add(entry.getKey());
            }
            entry.setValue(val);
        }
        for (Object key : removeKeys) {
            properties.remove(key);
        }
        return properties;
    }
}
