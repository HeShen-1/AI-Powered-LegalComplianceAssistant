package com.river.LegalAssistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Thymeleaf模板引擎配置
 * 配置支持Markdown文件的模板解析
 */
@Configuration
public class ThymeleafConfig {
    
    /**
     * 配置Markdown模板解析器
     */
    @Bean(name = "markdownTemplateResolver")
    public ClassLoaderTemplateResolver markdownTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".md");
        resolver.setTemplateMode(TemplateMode.TEXT);  // TEXT模式支持纯文本和Markdown
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        resolver.setCacheable(true);  // 生产环境启用缓存
        resolver.setOrder(1);  // 设置优先级
        return resolver;
    }
    
    /**
     * 配置模板引擎
     */
    @Bean(name = "markdownTemplateEngine")
    public TemplateEngine markdownTemplateEngine() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(markdownTemplateResolver());
        return templateEngine;
    }
}

