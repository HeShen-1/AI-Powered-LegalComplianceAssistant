package com.river.LegalAssistant.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 文档解析服务
 * 支持多种文件格式的统一解析处理
 */
@Service
@Slf4j
public class DocumentParserService {

    private final Parser parser;
    
    // 支持的文件类型
    private static final List<String> SUPPORTED_TYPES = Arrays.asList(
        "pdf", "docx", "doc", "txt", "rtf", "odt", "md"
    );
    
    // 最大文件大小限制 (10MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    public DocumentParserService() {
        this.parser = new AutoDetectParser();
    }

    /**
     * 统一文档解析方法
     * 
     * @param inputStream 文件输入流
     * @param fileName 文件名（用于类型推断）
     * @param fileSize 文件大小
     * @return 解析后的纯文本内容
     * @throws DocumentParsingException 解析异常
     */
    public String parseDocument(InputStream inputStream, String fileName, Long fileSize) 
            throws DocumentParsingException {
        
        if (inputStream == null) {
            throw new DocumentParsingException("输入流不能为空");
        }
        
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new DocumentParsingException("文件名不能为空");
        }
        
        // 检查文件大小
        if (fileSize != null && fileSize > MAX_FILE_SIZE) {
            throw new DocumentParsingException("文件大小超过限制（最大10MB）");
        }
        
        // 获取文件扩展名
        String fileExtension = getFileExtension(fileName);
        if (isFileTypeSupported(fileExtension)) {
            throw new DocumentParsingException("不支持的文件类型: " + fileExtension);
        }
        
        log.info("开始解析文档: {}, 类型: {}, 大小: {} bytes", fileName, fileExtension, fileSize);
        
        try {
            String content;
            
            // 根据文件类型选择解析策略
            if ("txt".equalsIgnoreCase(fileExtension) || "md".equalsIgnoreCase(fileExtension)) {
                // 纯文本文件直接读取
                content = parseTextFile(inputStream);
            } else {
                // 使用Tika解析其他类型文件
                content = parseWithTika(inputStream, fileName);
            }
            
            // 清理和验证内容
            content = cleanAndValidateContent(content);
            
            log.info("文档解析成功: {}, 内容长度: {} 字符", fileName, content.length());
            return content;
            
        } catch (Exception e) {
            log.error("文档解析失败: {}", fileName, e);
            throw new DocumentParsingException("解析文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用Tika解析文档
     */
    private String parseWithTika(InputStream inputStream, String fileName) throws IOException, SAXException, TikaException {
        // 设置内容处理器，限制内容长度避免内存溢出
        BodyContentHandler handler = new BodyContentHandler(10 * 1024 * 1024); // 10MB limit
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        
        // 设置文件名元数据，帮助Tika识别文件类型
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        
        // 执行解析
        parser.parse(inputStream, handler, metadata, context);
        
        String content = handler.toString();
        
        // 记录文档元数据
        logDocumentMetadata(metadata, fileName);
        
        return content;
    }

    /**
     * 解析纯文本文件
     */
    private String parseTextFile(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 清理和验证内容
     */
    private String cleanAndValidateContent(String content) throws DocumentParsingException {
        if (content == null || content.trim().isEmpty()) {
            throw new DocumentParsingException("文档内容为空或无法提取文本");
        }
        
        // 清理特殊字符和格式
        content = content
            .replace("\0", "")  // 移除空字符
            .replaceAll("\\s+", " ")  // 规范化空白字符
            .trim();
        
        // 检查最小内容长度
        if (content.length() < 10) {
            throw new DocumentParsingException("文档内容过短，可能解析失败");
        }
        
        return content;
    }

    /**
     * 记录文档元数据
     */
    private void logDocumentMetadata(Metadata metadata, String fileName) {
        try {
            String contentType = metadata.get(Metadata.CONTENT_TYPE);
            String author = metadata.get(TikaCoreProperties.CREATOR);
            String title = metadata.get(TikaCoreProperties.TITLE);
            String creationDate = metadata.get(TikaCoreProperties.CREATED);
            
            log.debug("文档元数据 - 文件: {}, 类型: {}, 作者: {}, 标题: {}, 创建日期: {}", 
                fileName, contentType, author, title, creationDate);
        } catch (Exception e) {
            log.warn("记录文档元数据失败: {}", e.getMessage());
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 检查文件类型是否支持
     */
    public boolean isFileTypeSupported(String fileExtension) {
        return !SUPPORTED_TYPES.contains(fileExtension.toLowerCase());
    }

    /**
     * 获取支持的文件类型列表
     */
    public List<String> getSupportedFileTypes() {
        return List.copyOf(SUPPORTED_TYPES);
    }

    /**
     * 文档解析异常类
     */
    public static class DocumentParsingException extends Exception {
        public DocumentParsingException(String message) {
            super(message);
        }

        public DocumentParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
