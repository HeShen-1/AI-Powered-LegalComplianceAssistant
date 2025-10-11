package com.river.LegalAssistant.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson JSON序列化配置
 * 解决Hibernate代理对象序列化和循环引用问题
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 注册Hibernate6模块来处理懒加载代理
        Hibernate6Module hibernate6Module = new Hibernate6Module();
        // 强制懒加载代理序列化，而不是抛出异常
        hibernate6Module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
        hibernate6Module.enable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        // 序列化标识符而不是完整对象来避免循环引用
        hibernate6Module.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
        mapper.registerModule(hibernate6Module);
        
        // 注册Java Time模块
        mapper.registerModule(new JavaTimeModule());
        
        // 配置序列化选项
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 忽略null值字段
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        // 设置更高的嵌套深度限制来处理复杂对象
        StreamWriteConstraints writeConstraints = StreamWriteConstraints.builder()
                .maxNestingDepth(2000)  // 增加最大嵌套深度限制
                .build();
        mapper.getFactory().setStreamWriteConstraints(writeConstraints);
        
        // 禁用非ASCII字符转义，确保SSE中的中文字符正常显示
        mapper.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
        
        return mapper;
    }
}
