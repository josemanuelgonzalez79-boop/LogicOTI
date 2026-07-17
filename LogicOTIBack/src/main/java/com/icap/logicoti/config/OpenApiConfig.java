package com.icap.logicoti.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI industrialOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Industrial Backend API")
                .version("1.0.0")
                .description("API base para proyectos industriales con Spring Boot."));
    }
}
