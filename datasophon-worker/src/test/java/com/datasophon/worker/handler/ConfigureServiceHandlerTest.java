package com.datasophon.worker.handler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

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

    @Test
    void replacePlaceholderDoesNotLogS3Credentials() throws Exception {
        Logger logger = mock(Logger.class);
        configureServiceHandlerUnderTest.setLogger(logger);
        Method method = ConfigureServiceHandler.class
                .getDeclaredMethod("replacePlaceholder", ServiceConfig.class, Map.class);
        method.setAccessible(true);

        for (String name : new String[] {"s3AccessKey", "s3SecretKey"}) {
            ServiceConfig config = new ServiceConfig();
            config.setName(name);
            config.setType(Constants.INPUT);
            config.setValue("credential-value");
            method.invoke(configureServiceHandlerUnderTest, config, Map.of());

            assertEquals("credential-value", config.getValue());
            verify(logger).info("config {} set value to {}", name, "<redacted>");
        }
    }
}
