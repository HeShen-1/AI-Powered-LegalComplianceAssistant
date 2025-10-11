package com.river.LegalAssistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 文档配置
 * 支持Knife4j增强文档和SpringDoc OpenAPI
 */
@Configuration
@Slf4j
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * OpenAPI 配置
     */
    @Bean
    public OpenAPI customOpenAPI() {
        log.info("初始化OpenAPI配置, contextPath: {}, port: {}", contextPath, serverPort);
        
        return new OpenAPI()
                .info(new Info()
                        .title("法律合规智能审查助手 API")
                        .description("基于 Spring AI + RAG + MCP 的法律合规智能审查系统")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("River")
                                .email("dev@legalassistant.com"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://opensource.org/licenses/Apache-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort + contextPath)
                                .description("本地开发环境"),
                        new Server()
                                .url("https://api.legalassistant.com" + contextPath)
                                .description("生产环境")))
                // JWT认证配置
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        // Bearer Token认证（JWT）
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT认证 - 格式: Bearer {token}"))
                        // 保留Basic认证配置（向后兼容）
                        .addSecuritySchemes("basicAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")
                                        .description("HTTP Basic 认证")));
    }
}
