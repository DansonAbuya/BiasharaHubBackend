package com.biasharahub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:5050}")
    private String serverPort;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        // Use resolved port so Swagger UI displays e.g. http://localhost:5050/api
        // instead of http://localhost:${SERVER_PORT:5050}/api
        String serverUrl = "http://localhost:" + serverPort + contextPath;
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("BiasharaHub API")
                        .version("1.0")
                        .description("BiasharaHub - Multi-tenant SME Commerce Platform API"))
                .servers(List.of(new Server().url(serverUrl).description("Local server")));
    }
}
