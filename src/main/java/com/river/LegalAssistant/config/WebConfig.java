package com.river.LegalAssistant.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 配置
 * 主要用于配置异步请求支持，特别是SSE长连接
 * 以及HTTP消息转换器的字符编码配置
 */
@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置异步请求支持
     * 设置更长的超时时间以支持AI分析等长时间任务
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        log.info("配置Web异步支持，设置超时为25分钟以支持长时间AI分析");
        
        // 设置异步请求超时时间为25分钟（1500秒）
        // 这主要影响SSE连接和其他异步请求
        configurer.setDefaultTimeout(25 * 60 * 1000L);
        
        // 设置任务执行器（可选，如果不设置会使用默认的）
        // configurer.setTaskExecutor(generalTaskExecutor());
    }

    /**
     * 配置HTTP消息转换器
     * 确保JSON响应中的中文字符正确编码
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 找到现有的Jackson转换器并配置它，而不是添加新的
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                MappingJackson2HttpMessageConverter jsonConverter = (MappingJackson2HttpMessageConverter) converter;
                ObjectMapper objectMapper = jsonConverter.getObjectMapper();
                
                // 禁用非ASCII字符转义，确保中文字符正常显示
                objectMapper.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
                
                log.info("配置现有JSON消息转换器，禁用非ASCII字符转义");
                break;
            }
        }
    }
}
