package com.datasophon.worker.handler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigureServiceHandlerTest {

    private ConfigureServiceHandler configureServiceHandlerUnderTest;

    @BeforeEach
    public void setUp() {
        configureServiceHandlerUnderTest = new ConfigureServiceHandler("HDFS", "NameNode");
    }

    @Test
    void replacePlaceholderUsesDefaultValueWhenValueIsMissing() throws Exception {
        ServiceConfig config = new ServiceConfig();
        config.setName("hostname");
        config.setType(Constants.INPUT);
        config.setDefaultValue("${host}");
        Method method = ConfigureServiceHandler.class
                .getDeclaredMethod("replacePlaceholder", ServiceConfig.class, Map.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(configureServiceHandlerUnderTest, config, Map.of("${host}", "ddh-01")));

        assertEquals("ddh-01", config.getValue());
    }
}
