package com.river.LegalAssistant.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * SSE相关配置
 * 确保SSE响应中的中文字符正确编码
 */
@Configuration
@Slf4j
public class SseConfig {

    /**
     * 配置ObjectMapper确保中文字符正确编码
     */
    @Bean
    public ObjectMapper sseObjectMapper() {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .build();
        
        // 禁用非ASCII字符转义，确保中文字符正常显示
        mapper.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
        
        // 确保输出格式化（可选，便于调试）
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        
        log.info("SSE ObjectMapper配置完成，禁用非ASCII字符转义");
        return mapper;
    }
}
