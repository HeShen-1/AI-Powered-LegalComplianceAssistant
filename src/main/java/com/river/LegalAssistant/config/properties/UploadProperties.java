package com.river.LegalAssistant.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件上传配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    
    /**
     * 最大文件大小
     */
    private String maxFileSize = "10MB";
    
    /**
     * 允许的文件类型
     */
    private List<String> allowedTypes = List.of("pdf", "docx", "txt", "doc", "md");
    
    /**
     * 文件存储路径
     */
    private String storagePath;
}
