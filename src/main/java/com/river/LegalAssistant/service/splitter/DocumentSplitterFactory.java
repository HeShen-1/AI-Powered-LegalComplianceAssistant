package com.river.LegalAssistant.service.splitter;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文档分割器工厂
 * 
 * 根据文档类型自动选择合适的分割器:
 * - 法律文档 → LegalDocumentSplitter (按条文分割)
 * - 合同文档 → ContractSplitter (按条款分割)
 * - 通用文档 → RecursiveSplitter (递归文本分割)
 * 
 * 设计模式: 工厂模式
 * 可扩展性: 支持注册新的文档分割器类型
 * 
 * @author River
 * @since 1.0
 */
@Component
@Slf4j
public class DocumentSplitterFactory {
    
    private final LegalDocumentSplitter legalDocumentSplitter;
    private final ContractSplitter contractSplitter;
    
    @Value("${app.etl.chunk-size:800}")
    private int defaultChunkSize;
    
    @Value("${app.etl.chunk-overlap:80}")
    private int defaultChunkOverlap;
    
    /**
     * 构造函数
     */
    public DocumentSplitterFactory(
            LegalDocumentSplitter legalDocumentSplitter,
            ContractSplitter contractSplitter) {
        this.legalDocumentSplitter = legalDocumentSplitter;
        this.contractSplitter = contractSplitter;
        log.info("DocumentSplitterFactory 初始化完成 - 已注册分割器: Legal, Contract, Recursive");
    }
    
    /**
     * 根据文档类型获取合适的分割器
     * 
     * @param documentType 文档类型 (LAW, CONTRACT_TEMPLATE, CASE, REGULATION等)
     * @return 文档分割器
     */
    public DocumentSplitter getSplitterByDocumentType(String documentType) {
        if (documentType == null) {
            log.debug("文档类型为空，使用默认递归分割器");
            return createRecursiveSplitter();
        }
        
        switch (documentType.toUpperCase()) {
            case "LAW":
            case "REGULATION":
                log.debug("使用法律文档分割器: {}", documentType);
                return legalDocumentSplitter;
                
            case "CONTRACT_TEMPLATE":
            case "CONTRACT":
                log.debug("使用合同文档分割器: {}", documentType);
                return contractSplitter;
                
            case "CASE":
            default:
                log.debug("使用通用递归分割器: {}", documentType);
                return createRecursiveSplitter();
        }
    }
    
    /**
     * 根据文件名智能识别并获取分割器
     * 
     * @param fileName 文件名
     * @return 文档分割器
     */
    public DocumentSplitter getSplitterByFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            log.debug("文件名为空，使用默认递归分割器");
            return createRecursiveSplitter();
        }
        
        String lowerName = fileName.toLowerCase();
        
        // 法律文档关键词匹配
        if (isLegalDocument(lowerName)) {
            log.debug("文件名 {} 识别为法律文档，使用法律文档分割器", fileName);
            return legalDocumentSplitter;
        }
        
        // 合同文档关键词匹配
        if (isContractDocument(lowerName)) {
            log.debug("文件名 {} 识别为合同文档，使用合同文档分割器", fileName);
            return contractSplitter;
        }
        
        // 默认使用递归分割器
        log.debug("文件名 {} 使用通用递归分割器", fileName);
        return createRecursiveSplitter();
    }
    
    /**
     * 获取法律文档分割器
     */
    public DocumentSplitter getLegalSplitter() {
        return legalDocumentSplitter;
    }
    
    /**
     * 获取合同文档分割器
     */
    public DocumentSplitter getContractSplitter() {
        return contractSplitter;
    }
    
    /**
     * 获取通用递归分割器
     */
    public DocumentSplitter getRecursiveSplitter() {
        return createRecursiveSplitter();
    }
    
    /**
     * 创建自定义配置的递归分割器
     */
    public DocumentSplitter createRecursiveSplitter(int chunkSize, int chunkOverlap) {
        return DocumentSplitters.recursive(chunkSize, chunkOverlap);
    }
    
    /**
     * 创建默认递归分割器
     */
    private DocumentSplitter createRecursiveSplitter() {
        return DocumentSplitters.recursive(defaultChunkSize, defaultChunkOverlap);
    }
    
    /**
     * 判断是否为法律文档
     */
    private boolean isLegalDocument(String fileName) {
        String[] legalKeywords = {
            "法", "law", "法律", "法规", "条例", "规定", 
            "民法", "刑法", "宪法", "行政法", "诉讼法",
            "规章", "办法", "细则"
        };
        
        for (String keyword : legalKeywords) {
            if (fileName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断是否为合同文档
     */
    private boolean isContractDocument(String fileName) {
        String[] contractKeywords = {
            "合同", "contract", "协议", "agreement", 
            "契约", "条款", "terms"
        };
        
        for (String keyword : contractKeywords) {
            if (fileName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取分割器类型描述
     */
    public String getSplitterType(DocumentSplitter splitter) {
        if (splitter == legalDocumentSplitter) {
            return "LegalDocumentSplitter";
        } else if (splitter == contractSplitter) {
            return "ContractSplitter";
        } else {
            return "RecursiveSplitter";
        }
    }
}

