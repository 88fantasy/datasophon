package com.datasophon.api.configuration;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

/**
 * @author zhanghuangbin
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    @ConditionalOnProperty(name = {"springdoc.api-docs.enabled"})
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("VOS")
                .description("vos master节点接口")
                .version("2.0")
                .contact(
                        new Contact()
                                .name("datasophon")
                                .email("88fantasy@gmail.com"));
        return new OpenAPI().info(info);
    }

    @Bean
    @ConditionalOnProperty(name = {"springdoc.api-docs.enabled"})
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .pathsToMatch("/internal/**")
                .build();
    }
}
