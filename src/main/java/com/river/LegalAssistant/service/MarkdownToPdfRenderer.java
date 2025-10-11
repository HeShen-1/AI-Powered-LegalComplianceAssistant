package com.river.LegalAssistant.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.ListNumberingType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简化版Markdown到PDF渲染工具
 * 使用正则表达式处理常见的Markdown语法，避免复杂的依赖冲突
 */
@Component
@Slf4j
public class MarkdownToPdfRenderer {

    // 默认颜色
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(41, 128, 185);      // 蓝色
    private static final DeviceRgb SUCCESS_COLOR = new DeviceRgb(39, 174, 96);       // 绿色
    private static final DeviceRgb WARNING_COLOR = new DeviceRgb(241, 196, 15);      // 黄色
    private static final DeviceRgb DANGER_COLOR = new DeviceRgb(231, 76, 60);        // 红色
    private static final DeviceRgb GRAY_COLOR = new DeviceRgb(149, 165, 166);        // 灰色

    // Markdown正则表达式
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern BULLET_LIST_PATTERN = Pattern.compile("^[\\s]*[-*+]\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern NUMBERED_LIST_PATTERN = Pattern.compile("^[\\s]*\\d+\\.\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*([^*]+?)\\*");
    private static final Pattern CODE_PATTERN = Pattern.compile("`([^`]+?)`");
    
    /**
     * 将Markdown内容渲染到PDF文档中
     * @param document PDF文档对象
     * @param markdownContent Markdown格式的内容
     * @param chineseFont 中文字体
     * @param chineseBoldFont 中文粗体字体
     */
    public void renderMarkdownToPdf(Document document, String markdownContent, 
                                  PdfFont chineseFont, PdfFont chineseBoldFont) {
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            log.warn("Markdown内容为空，跳过渲染");
            return;
        }

        try {
            if (log.isDebugEnabled()) {
                log.debug("开始渲染Markdown内容，总长度: {} 字符", markdownContent.length());
            }
            
            // 超严格预处理：使用最安全的字符过滤
            String cleanedContent = preprocessMarkdownContentStrict(markdownContent);
            if (log.isDebugEnabled()) {
                log.debug("严格清理后内容长度: {} 字符", cleanedContent.length());
            }
            
            // 按行处理内容
            String[] lines = cleanedContent.split("\n");
            if (log.isDebugEnabled()) {
                log.debug("Markdown分割为 {} 行，开始逐行渲染", lines.length);
            }
            
            processLines(document, lines, chineseFont, chineseBoldFont);
            
            if (log.isDebugEnabled()) {
                log.debug("Markdown渲染完成");
            }
            
        } catch (Exception e) {
            log.error("Markdown解析失败，回退到安全的纯文本显示", e);
            // 安全的回退处理
            addSafeFallbackContent(document, markdownContent, chineseFont);
        }
    }
    
    /**
     * 预处理Markdown内容
     * 清理可能导致问题的字符和格式，增强PDF字体兼容性
     */
    private String preprocessMarkdownContent(String content) {
        if (content == null) {
            return "";
        }
        
        // 第一步：基础清理
        // 移除不可打印的控制字符（保留换行符、制表符等常用字符）
        content = content.replaceAll("[\\x00-\\x08\\x0B-\\x0C\\x0E-\\x1F]", "");
        
        // 处理常见的HTML实体
        content = content.replace("&nbsp;", " ");
        content = content.replace("&lt;", "<");
        content = content.replace("&gt;", ">");
        content = content.replace("&amp;", "&");
        content = content.replace("&quot;", "\"");
        
        // 规范化换行符
        content = content.replace("\r\n", "\n");
        content = content.replace("\r", "\n");
        
        // 第二步：PDF字体兼容性处理
        // 使用更严格的字符过滤，确保PDF字体支持
        StringBuilder safeContent = new StringBuilder();
        for (char c : content.toCharArray()) {
            // 只保留PDF字体支持的字符范围
            if ((c >= 0x20 && c <= 0x7E) ||     // 基本ASCII可打印字符
                (c >= 0x4E00 && c <= 0x9FFF) ||  // CJK统一汉字
                (c >= 0x3400 && c <= 0x4DBF) ||  // CJK扩展A
                (c >= 0x3000 && c <= 0x303F) ||  // CJK符号和标点
                (c >= 0xFF00 && c <= 0xFFEF) ||  // 全角ASCII、全角标点
                c == '\n' || c == '\r' || c == '\t' || // 换行和制表符
                // 常用中文标点符号（只保留字体支持的基本标点）
                c == '，' || c == '。' || c == '；' || c == '：' || c == '？' || c == '！') {
                safeContent.append(c);
            } else if (c >= 0x2500 && c <= 0x257F) {
                // 盒绘制字符替换为安全字符
                if (c >= 0x2500 && c <= 0x253F) {
                    safeContent.append('-'); // 水平线类字符
                } else {
                    safeContent.append('|'); // 垂直线和其他复杂框线字符
                }
            } else if (c >= 0x2550 && c <= 0x256C) {
                // 双线框字符替换
                safeContent.append('=');
            } else if (c == '\u201C' || c == '\u201D') {
                // 左右双引号替换为普通双引号
                safeContent.append('"');
            } else if (c == '\u2018' || c == '\u2019') {
                // 左右单引号替换为普通单引号
                safeContent.append('\'');
            } else if (c == '…') {
                // 省略号替换为三个点
                safeContent.append("...");
            } else if (c == '—' || c == '–') {
                // 长短破折号替换为普通连字符
                safeContent.append('-');
            } else {
                // 其他不支持的字符忽略或替换为空格
                if (Character.isWhitespace(c)) {
                    safeContent.append(' ');
                }
                // 其他字符直接忽略
            }
        }
        
        String result = safeContent.toString();
        log.debug("Markdown内容预处理完成，原长度: {}, 处理后长度: {}", 
                 content.length(), result.length());
        
        return result;
    }

    /**
     * 超严格的Markdown内容预处理 - 彻底解决PDF字体兼容性
     * 使用与ReportGenerationService相同的白名单策略
     */
    private String preprocessMarkdownContentStrict(String content) {
        if (content == null) {
            return "";
        }
        
        log.debug("开始超严格Markdown预处理，原始长度: {}", content.length());
        
        StringBuilder safeContent = new StringBuilder();
        
        for (char c : content.toCharArray()) {
            // 使用与ReportGenerationService完全一致的白名单策略
            if ((c >= 'A' && c <= 'Z') ||        // 大写英文字母
                (c >= 'a' && c <= 'z') ||        // 小写英文字母  
                (c >= '0' && c <= '9') ||        // 数字
                (c >= 0x4E00 && c <= 0x9FFF) ||  // CJK统一汉字（基本区）
                // 基本ASCII标点（确定所有字体都支持）
                c == '.' || c == ',' || c == '!' || c == '?' ||
                c == ':' || c == ';' || c == '(' || c == ')' ||
                c == '[' || c == ']' || c == '{' || c == '}' ||
                c == '"' || c == '\'' || c == '-' || c == '_' ||
                c == '/' || c == '\\' || c == '|' || c == '+' ||
                c == '=' || c == '*' || c == '#' || c == '@' ||
                c == '%' || c == '&' || c == '^' || c == '~' ||
                c == '`' || c == '<' || c == '>' ||
                // 常用中文标点（直接保留）
                c == '，' || c == '。' || c == '；' || c == '：' ||
                c == '？' || c == '！' || c == '、' ||  // 保留顿号
                c == '（' || c == '）' || c == '《' || c == '》' ||
                c == '【' || c == '】' || c == '「' || c == '」' ||
                // 基本空白字符
                c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                safeContent.append(c);
            } else {
                // 严格的字符安全替换
                switch (c) {
                    // 特殊符号替换
                    case '\u201C', '\u201D' -> safeContent.append('"');
                    case '\u2018', '\u2019' -> safeContent.append('\'');
                    case '…' -> safeContent.append("...");
                    case '—', '–' -> safeContent.append('-');
                    case '·' -> safeContent.append('*');
                    // 完全忽略其他字符
                    default -> {
                        if (Character.isWhitespace(c)) {
                            safeContent.append(' ');
                        }
                        // 其他字符完全忽略
                    }
                }
            }
        }
        
        String result = safeContent.toString().trim();
        // 清理多余的空格（但保留换行符，避免破坏Markdown结构）
        result = result.replaceAll("[ \\t]+", " ");  // 只压缩空格和制表符，保留换行符
        result = result.replaceAll("\\n{3,}", "\n\n");  // 将3个及以上的连续换行压缩为2个
        
        log.debug("超严格Markdown预处理完成，处理后长度: {}", result.length());
        
        return result;
    }

    /**
     * 处理文本行
     */
    private void processLines(Document document, String[] lines, PdfFont chineseFont, PdfFont chineseBoldFont) {
        int i = 0;
        int processedLines = 0;
        
        while (i < lines.length) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // 跳过空行
            if (trimmedLine.isEmpty()) {
                i++;
                continue;
            }
            
            try {
                // 处理标题
                if (trimmedLine.startsWith("#")) {
                    addHeading(document, trimmedLine, chineseFont, chineseBoldFont);
                    i++;
                    processedLines++;
                    continue;
                }
                
                // 处理表格（检测表头和分隔行）
                if (trimmedLine.startsWith("|") && i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim();
                    if (isTableSeparatorLine(nextLine)) {
                        int newIndex = processTable(document, lines, i, chineseFont, chineseBoldFont);
                        processedLines += (newIndex - i);
                        i = newIndex;
                        continue;
                    }
                }
                
                // 处理无序列表
                if (trimmedLine.matches("^[\\s]*[-*+]\\s+.+$")) {
                    int newIndex = processBulletList(document, lines, i, chineseFont, chineseBoldFont);
                    processedLines += (newIndex - i);
                    i = newIndex;
                    continue;
                }
                
                // 处理有序列表
                if (trimmedLine.matches("^[\\s]*\\d+\\.\\s+.+$")) {
                    int newIndex = processNumberedList(document, lines, i, chineseFont, chineseBoldFont);
                    processedLines += (newIndex - i);
                    i = newIndex;
                    continue;
                }
                
                // 处理代码块
                if (trimmedLine.startsWith("```")) {
                    int newIndex = processCodeBlock(document, lines, i, chineseFont);
                    processedLines += (newIndex - i);
                    i = newIndex;
                    continue;
                }
                
                // 处理引用块
                if (trimmedLine.startsWith(">")) {
                    int newIndex = processBlockQuote(document, lines, i, chineseFont);
                    processedLines += (newIndex - i);
                    i = newIndex;
                    continue;
                }
                
                // 处理分隔线
                if (trimmedLine.matches("^[-*_]{3,}$")) {
                    addDivider(document);
                    i++;
                    processedLines++;
                    continue;
                }
                
                // 处理普通段落
                addParagraph(document, trimmedLine, chineseFont, chineseBoldFont);
                i++;
                processedLines++;
                
            } catch (Exception e) {
                log.warn("处理第 {} 行时出错: {}, 跳过该行", i, e.getMessage());
                i++;
            }
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Markdown处理完成，总行数: {}, 已处理: {}", lines.length, processedLines);
        }
    }
    
    /**
     * 添加分隔线（使用字体兼容的字符）
     */
    private void addDivider(Document document) {
        // 使用普通的减号字符，所有字体都支持
        Paragraph divider = new Paragraph("-".repeat(80))
                .setFontColor(GRAY_COLOR)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10)
                .setMarginBottom(10);
        document.add(divider);
    }
    
    /**
     * 处理代码块
     */
    private int processCodeBlock(Document document, String[] lines, int startIndex, PdfFont chineseFont) {
        int i = startIndex + 1; // 跳过开始的```
        StringBuilder codeContent = new StringBuilder();
        
        // 收集代码块内容
        while (i < lines.length) {
            String line = lines[i];
            if (line.trim().startsWith("```")) {
                i++; // 跳过结束的```
                break;
            }
            codeContent.append(line).append("\n");
            i++;
        }
        
        // 添加代码块到文档
        if (codeContent.length() > 0) {
            Paragraph codeBlock = new Paragraph(codeContent.toString())
                    .setFontSize(9)
                    .setBackgroundColor(new DeviceRgb(245, 245, 245))
                    .setFontColor(new DeviceRgb(84, 110, 122))
                    .setPadding(10)
                    .setMarginBottom(10);
            
            if (chineseFont != null) {
                codeBlock.setFont(chineseFont);
            }
            
            document.add(codeBlock);
        }
        
        return i;
    }
    
    /**
     * 处理引用块
     */
    private int processBlockQuote(Document document, String[] lines, int startIndex, PdfFont chineseFont) {
        int i = startIndex;
        StringBuilder quoteContent = new StringBuilder();
        
        // 收集引用内容
        while (i < lines.length) {
            String line = lines[i].trim();
            if (!line.startsWith(">")) {
                break;
            }
            String quoteLine = line.substring(1).trim();
            if (!quoteLine.isEmpty()) {
                quoteContent.append(quoteLine).append("\n");
            }
            i++;
        }
        
        // 添加引用块到文档
        if (quoteContent.length() > 0) {
            Paragraph quoteBlock = new Paragraph(quoteContent.toString())
                    .setFontSize(10)
                    .setItalic()
                    .setFontColor(GRAY_COLOR)
                    .setBackgroundColor(new DeviceRgb(248, 249, 250))
                    .setPaddingLeft(15)
                    .setPaddingRight(10)
                    .setPaddingTop(10)
                    .setPaddingBottom(10)
                    .setMarginLeft(10)
                    .setMarginBottom(10);
            
            if (chineseFont != null) {
                quoteBlock.setFont(chineseFont);
            }
            
            document.add(quoteBlock);
        }
        
        return i;
    }

    /**
     * 添加标题
     */
    private void addHeading(Document document, String line, PdfFont chineseFont, PdfFont chineseBoldFont) {
        Matcher matcher = HEADING_PATTERN.matcher(line);
        if (matcher.find()) {
            String hashes = matcher.group(1);
            String text = matcher.group(2);
            int level = hashes.length();
            
            float fontSize = switch (level) {
                case 1 -> 16f;
                case 2 -> 14f;
                case 3 -> 13f;
                case 4 -> 12f;
                default -> 11f;
            };
            
            Paragraph heading = new Paragraph()
                    .setFontSize(fontSize)
                    .setMarginBottom(8)
                    .setMarginTop(level == 1 ? 15 : 10)
                    .setBold();
                    
            if (chineseBoldFont != null) {
                heading.setFont(chineseBoldFont);
            }
            
            // 根据标题级别设置颜色
            DeviceRgb color = switch (level) {
                case 1 -> PRIMARY_COLOR;
                case 2 -> new DeviceRgb(52, 152, 219);
                case 3 -> new DeviceRgb(46, 125, 50);
                default -> new DeviceRgb(69, 90, 100);
            };
            heading.setFontColor(color);
            
            heading.add(text);
            document.add(heading);
        }
    }

    /**
     * 处理无序列表（改进版，避免符号显示问题）
     */
    private int processBulletList(Document document, String[] lines, int startIndex, PdfFont chineseFont, PdfFont chineseBoldFont) {
        int i = startIndex;
        int itemIndex = 1;
        
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.matches("^[\\s]*[-*+]\\s+.+$")) {
                String text = line.replaceFirst("^[\\s]*[-*+]\\s+", "");
                
                // 创建带编号的列表项段落，避免使用符号
                Paragraph listItem = new Paragraph()
                        .setFontSize(11)
                        .setMarginLeft(15)
                        .setMarginBottom(6);
                        
                if (chineseFont != null) {
                    listItem.setFont(chineseFont);
                }
                
                // 添加编号前缀
                Text itemPrefix = new Text(itemIndex + ". ");
                itemPrefix.setBold();
                if (chineseFont != null) {
                    itemPrefix.setFont(chineseFont);
                }
                listItem.add(itemPrefix);
                
                // 添加列表项内容，处理内联格式
                addFormattedTextToParagraph(listItem, text, chineseFont, chineseBoldFont);
                
                document.add(listItem);
                itemIndex++;
                i++;
            } else {
                break;
            }
        }
        
        return i;
    }

    /**
     * 处理有序列表
     */
    private int processNumberedList(Document document, String[] lines, int startIndex, PdfFont chineseFont, PdfFont chineseBoldFont) {
        List list = new List(ListNumberingType.DECIMAL)
                .setSymbolIndent(10)
                .setMarginBottom(10);
                
        int i = startIndex;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.matches("^[\\s]*\\d+\\.\\s+.+$")) {
                String text = line.replaceFirst("^[\\s]*\\d+\\.\\s+", "");
                
                // 创建列表项
                ListItem item = new ListItem();
                item.setFontSize(10);
                item.setMarginBottom(3);
                
                // 使用段落处理内联格式（粗体、斜体等）
                Paragraph itemParagraph = new Paragraph();
                if (chineseFont != null) {
                    itemParagraph.setFont(chineseFont);
                }
                addFormattedTextToParagraph(itemParagraph, text, chineseFont, chineseBoldFont);
                item.add(itemParagraph);
                
                list.add(item);
                i++;
            } else {
                break;
            }
        }
        
        if (list.getChildren().size() > 0) {
            document.add(list);
        }
        return i;
    }

    /**
     * 添加段落
     */
    private void addParagraph(Document document, String line, PdfFont chineseFont, PdfFont chineseBoldFont) {
        Paragraph paragraph = new Paragraph()
                .setFontSize(11)
                .setMarginBottom(8);
                
        if (chineseFont != null) {
            paragraph.setFont(chineseFont);
        }
        
        // 处理内联格式
        addFormattedTextToParagraph(paragraph, line, chineseFont, chineseBoldFont);
        
        document.add(paragraph);
    }

    /**
     * 添加格式化文本到段落（修复重复内容BUG）
     */
    private void addFormattedTextToParagraph(Paragraph paragraph, String text, PdfFont chineseFont, PdfFont chineseBoldFont) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        try {
            // 处理粗体
            Matcher boldMatcher = BOLD_PATTERN.matcher(text);
            int lastEnd = 0;
            boolean foundBold = false;
            
            while (boldMatcher.find()) {
                foundBold = true;
                // 安全地获取粗体前的文本
                if (boldMatcher.start() > lastEnd) {
                    String beforeBold = text.substring(lastEnd, boldMatcher.start());
                    if (!beforeBold.isEmpty()) {
                        addSimpleTextToParagraph(paragraph, beforeBold, chineseFont);
                    }
                }
                
                // 添加粗体文本
                String boldContent = boldMatcher.group(1);
                if (boldContent != null && !boldContent.isEmpty()) {
                    Text boldText = new Text(boldContent).setBold();
                    if (chineseBoldFont != null) {
                        try {
                            boldText.setFont(chineseBoldFont);
                        } catch (Exception e) {
                            if (log.isDebugEnabled()) {
                                log.debug("设置粗体字体失败，使用默认字体: {}", e.getMessage());
                            }
                        }
                    }
                    paragraph.add(boldText);
                }
                
                lastEnd = boldMatcher.end();
            }
            
            // 添加剩余文本（只有找到粗体时才处理剩余部分）
            if (foundBold && lastEnd < text.length()) {
                String remaining = text.substring(lastEnd);
                if (!remaining.isEmpty()) {
                    addSimpleTextToParagraph(paragraph, remaining, chineseFont);
                }
            }
            
            // 如果没有找到粗体标记，直接添加整个文本
            if (!foundBold) {
                addSimpleTextToParagraph(paragraph, text, chineseFont);
            }
            
        } catch (Exception e) {
            log.warn("格式化文本处理失败，使用安全回退: {}", e.getMessage());
            // 安全回退：直接添加纯文本
            addSimpleTextToParagraph(paragraph, text, chineseFont);
        }
    }

    /**
     * 添加简单文本到段落（处理斜体和代码）- 修复重复内容BUG
     */
    private void addSimpleTextToParagraph(Paragraph paragraph, String text, PdfFont chineseFont) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        try {
            // 处理斜体
            Matcher italicMatcher = ITALIC_PATTERN.matcher(text);
            int lastEnd = 0;
            boolean foundItalic = false;
            
            while (italicMatcher.find()) {
                foundItalic = true;
                // 安全地添加斜体前的文本
                if (italicMatcher.start() > lastEnd) {
                    String beforeText = text.substring(lastEnd, italicMatcher.start());
                    if (!beforeText.isEmpty()) {
                        addCodeAndPlainText(paragraph, beforeText, chineseFont);
                    }
                }
                
                // 添加斜体文本
                String italicContent = italicMatcher.group(1);
                if (italicContent != null && !italicContent.isEmpty()) {
                    Text italicText = new Text(italicContent).setItalic();
                    if (chineseFont != null) {
                        try {
                            italicText.setFont(chineseFont);
                        } catch (Exception e) {
                            if (log.isDebugEnabled()) {
                                log.debug("设置斜体字体失败，使用默认字体: {}", e.getMessage());
                            }
                        }
                    }
                    paragraph.add(italicText);
                }
                
                lastEnd = italicMatcher.end();
            }
            
            // 添加剩余文本（只有找到斜体时才处理剩余部分）
            if (foundItalic && lastEnd < text.length()) {
                String remaining = text.substring(lastEnd);
                if (!remaining.isEmpty()) {
                    addCodeAndPlainText(paragraph, remaining, chineseFont);
                }
            }
            
            // 如果没有找到斜体标记，直接添加整个文本
            if (!foundItalic) {
                addCodeAndPlainText(paragraph, text, chineseFont);
            }
            
        } catch (Exception e) {
            log.warn("斜体文本处理失败，使用安全回退: {}", e.getMessage());
            // 安全回退：直接添加纯文本
            addCodeAndPlainText(paragraph, text, chineseFont);
        }
    }

    /**
     * 添加代码和纯文本到段落 - 修复重复内容BUG
     */
    private void addCodeAndPlainText(Paragraph paragraph, String text, PdfFont chineseFont) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        try {
            // 处理代码
            Matcher codeMatcher = CODE_PATTERN.matcher(text);
            int lastEnd = 0;
            boolean foundCode = false;
            
            while (codeMatcher.find()) {
                foundCode = true;
                // 安全地添加代码前的文本
                if (codeMatcher.start() > lastEnd) {
                    String plainText = text.substring(lastEnd, codeMatcher.start());
                    if (!plainText.isEmpty()) {
                        Text normalText = new Text(plainText);
                        if (chineseFont != null) {
                            try {
                                normalText.setFont(chineseFont);
                            } catch (Exception e) {
                                if (log.isDebugEnabled()) {
                                    log.debug("设置普通文本字体失败，使用默认字体: {}", e.getMessage());
                                }
                            }
                        }
                        paragraph.add(normalText);
                    }
                }
                
                // 添加代码文本
                String codeContent = codeMatcher.group(1);
                if (codeContent != null && !codeContent.isEmpty()) {
                    Text codeText = new Text(codeContent)
                            .setFontColor(new DeviceRgb(84, 110, 122))
                            .setBackgroundColor(new DeviceRgb(245, 245, 245));
                    paragraph.add(codeText);
                }
                
                lastEnd = codeMatcher.end();
            }
            
            // 添加剩余文本（只有找到代码标记时才处理剩余部分）
            if (foundCode && lastEnd < text.length()) {
                String remaining = text.substring(lastEnd);
                if (!remaining.isEmpty()) {
                    Text normalText = new Text(remaining);
                    if (chineseFont != null) {
                        try {
                            normalText.setFont(chineseFont);
                        } catch (Exception e) {
                            if (log.isDebugEnabled()) {
                                log.debug("设置剩余文本字体失败，使用默认字体: {}", e.getMessage());
                            }
                        }
                    }
                    paragraph.add(normalText);
                }
            }
            
            // 如果没有找到代码标记，直接添加整个文本
            if (!foundCode) {
                Text normalText = new Text(text);
                if (chineseFont != null) {
                    try {
                        normalText.setFont(chineseFont);
                    } catch (Exception e) {
                        if (log.isDebugEnabled()) {
                            log.debug("设置文本字体失败，使用默认字体: {}", e.getMessage());
                        }
                    }
                }
                paragraph.add(normalText);
            }
            
        } catch (Exception e) {
            log.warn("代码和纯文本处理失败，使用安全回退: {}", e.getMessage());
            // 安全回退：直接添加纯文本
            try {
                Text safeText = new Text(text);
                paragraph.add(safeText);
            } catch (Exception fallbackError) {
                log.error("连安全回退也失败了: {}", fallbackError.getMessage());
            }
        }
    }

    /**
     * 处理内联格式（简化版本，用于列表项）
     */
    private String processInlineFormatting(String text) {
        // 简单移除markdown标记，保留纯文本
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1"); // 移除粗体标记
        text = text.replaceAll("\\*([^*]+?)\\*", "$1");    // 移除斜体标记
        text = text.replaceAll("`([^`]+?)`", "$1");        // 移除代码标记
        return text;
    }
    
    /**
     * 检测是否为表格分隔行
     * 例如: |---|---|---| 或 |:---|:---:|---:|
     */
    private boolean isTableSeparatorLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        String trimmed = line.trim();
        // 检测表格分隔行格式: |---|---|
        return trimmed.matches("^\\|[\\s:|-]+\\|$");
    }
    
    /**
     * 处理Markdown表格
     */
    private int processTable(Document document, String[] lines, int startIndex, 
                           PdfFont chineseFont, PdfFont chineseBoldFont) {
        int i = startIndex;
        java.util.List<String[]> tableData = new ArrayList<>();
        
        // 读取表头
        String headerLine = lines[i].trim();
        String[] headers = parseTableRow(headerLine);
        tableData.add(headers);
        i++;
        
        // 跳过分隔行
        if (i < lines.length && isTableSeparatorLine(lines[i])) {
            i++;
        }
        
        // 读取表格数据行
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.isEmpty() || !line.startsWith("|")) {
                break;
            }
            String[] rowData = parseTableRow(line);
            tableData.add(rowData);
            i++;
        }
        
        // 创建PDF表格
        if (!tableData.isEmpty() && headers.length > 0) {
            createPdfTable(document, tableData, chineseFont, chineseBoldFont);
        }
        
        return i;
    }
    
    /**
     * 解析表格行，提取单元格内容
     */
    private String[] parseTableRow(String line) {
        if (line == null || line.trim().isEmpty()) {
            return new String[0];
        }
        
        // 去掉首尾的 |
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        
        // 分割单元格
        String[] cells = trimmed.split("\\|");
        for (int i = 0; i < cells.length; i++) {
            cells[i] = cells[i].trim();
        }
        
        return cells;
    }
    
    /**
     * 创建PDF表格
     */
    private void createPdfTable(Document document, java.util.List<String[]> tableData,
                               PdfFont chineseFont, PdfFont chineseBoldFont) {
        if (tableData.isEmpty()) {
            return;
        }
        
        String[] headers = tableData.get(0);
        int columnCount = headers.length;
        
        // 创建等宽列的表格
        float[] columnWidths = new float[columnCount];
        for (int i = 0; i < columnCount; i++) {
            columnWidths[i] = 1f;
        }
        
        Table table = new Table(UnitValue.createPercentArray(columnWidths))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(10);
        
        // 添加表头
        for (String header : headers) {
            Cell headerCell = new Cell()
                    .add(createCellParagraph(header, chineseFont, chineseBoldFont, true))
                    .setBackgroundColor(new DeviceRgb(52, 152, 219))
                    .setFontColor(new DeviceRgb(255, 255, 255))
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBorder(new SolidBorder(new DeviceRgb(200, 200, 200), 1))
                    .setPadding(5);
            
            table.addHeaderCell(headerCell);
        }
        
        // 添加数据行
        boolean alternateRow = false;
        for (int i = 1; i < tableData.size(); i++) {
            String[] rowData = tableData.get(i);
            for (int j = 0; j < Math.min(rowData.length, columnCount); j++) {
                String cellContent = rowData[j];
                
                Cell dataCell = new Cell()
                        .add(createCellParagraph(cellContent, chineseFont, chineseBoldFont, false))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setBorder(new SolidBorder(new DeviceRgb(200, 200, 200), 1))
                        .setPadding(5);
                
                // 交替行背景色
                if (alternateRow) {
                    dataCell.setBackgroundColor(new DeviceRgb(248, 249, 250));
                }
                
                table.addCell(dataCell);
            }
            alternateRow = !alternateRow;
        }
        
        document.add(table);
    }
    
    /**
     * 创建表格单元格段落（支持粗体等格式）
     */
    private Paragraph createCellParagraph(String content, PdfFont chineseFont, 
                                         PdfFont chineseBoldFont, boolean isHeader) {
        Paragraph paragraph = new Paragraph()
                .setFontSize(isHeader ? 11 : 10)
                .setMargin(0);
        
        if (chineseFont != null) {
            paragraph.setFont(chineseFont);
        }
        
        // 处理粗体文本
        if (content.contains("**")) {
            Matcher boldMatcher = BOLD_PATTERN.matcher(content);
            int lastEnd = 0;
            
            while (boldMatcher.find()) {
                // 添加粗体前的文本
                if (boldMatcher.start() > lastEnd) {
                    String beforeText = content.substring(lastEnd, boldMatcher.start());
                    Text normalText = new Text(beforeText);
                    if (chineseFont != null) {
                        normalText.setFont(chineseFont);
                    }
                    paragraph.add(normalText);
                }
                
                // 添加粗体文本
                Text boldText = new Text(boldMatcher.group(1)).setBold();
                if (chineseBoldFont != null) {
                    boldText.setFont(chineseBoldFont);
                }
                paragraph.add(boldText);
                
                lastEnd = boldMatcher.end();
            }
            
            // 添加剩余文本
            if (lastEnd < content.length()) {
                String remaining = content.substring(lastEnd);
                Text normalText = new Text(remaining);
                if (chineseFont != null) {
                    normalText.setFont(chineseFont);
                }
                paragraph.add(normalText);
            }
        } else {
            // 没有粗体，直接添加
            Text text = new Text(content);
            if (chineseFont != null) {
                text.setFont(chineseFont);
            }
            paragraph.add(text);
        }
        
        return paragraph;
    }

    /**
     * 添加安全的回退内容，确保即使在极端情况下也能正常显示
     */
    private void addSafeFallbackContent(Document document, String originalContent, PdfFont chineseFont) {
        try {
            log.warn("启用安全回退模式，原始内容长度: {} 字符", originalContent.length());
            
            // 使用最严格的字符过滤
            StringBuilder safeContent = new StringBuilder();
            for (char c : originalContent.toCharArray()) {
                // 只保留绝对安全的字符
                if ((c >= 0x20 && c <= 0x7E) ||     // 基本ASCII
                    (c >= 0x4E00 && c <= 0x9FFF) ||  // 中文汉字
                    c == '\n' || c == ' ') {         // 换行和空格
                    safeContent.append(c);
                } else if (c == '\r' || c == '\t') {
                    safeContent.append(' '); // 替换为空格
                } else if (Character.isWhitespace(c)) {
                    safeContent.append(' '); // 其他空白字符替换为空格
                }
                // 其他字符直接忽略
            }
            
            String finalContent = safeContent.toString().trim();
            if (finalContent.isEmpty()) {
                finalContent = "[PDF渲染失败 - 内容包含不兼容字符]";
            }
            
            // 创建安全的段落
            Paragraph safeParagraph = new Paragraph(finalContent)
                    .setFontSize(10)
                    .setMarginBottom(10)
                    .setFontColor(new DeviceRgb(100, 100, 100)); // 灰色表示这是回退内容
                    
            if (chineseFont != null) {
                safeParagraph.setFont(chineseFont);
            }
            
            document.add(safeParagraph);
            if (log.isDebugEnabled()) {
                log.debug("✓ 安全回退内容已添加，最终长度: {} 字符", finalContent.length());
            }
            
        } catch (Exception fallbackError) {
            log.error("安全回退也失败了，添加错误提示", fallbackError);
            try {
                // 最后的回退：添加简单的错误信息
                Paragraph errorParagraph = new Paragraph("[内容渲染失败]")
                        .setFontSize(10)
                        .setFontColor(DANGER_COLOR);
                document.add(errorParagraph);
            } catch (Exception finalError) {
                log.error("连最基本的错误提示也失败了", finalError);
            }
        }
    }
}
