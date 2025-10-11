package com.river.LegalAssistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * DeepSeek 专用服务
 * 
 * 根据Spring AI官方文档实现DeepSeek特有功能：
 * 1. 推理模型（deepseek-reasoner）- 提供思维链推理
 * 2. 前缀补全 - 用于代码生成等场景
 * 3. 多轮对话 - 正确处理推理内容
 * 
 * 官方文档: https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.deepseek.api-key")
public class DeepSeekService {

    @Qualifier("advancedChatClient")
    private final ChatClient deepSeekChatClient;

    /**
     * 使用DeepSeek推理模型进行复杂推理
     * 
     * @param question 需要推理的问题
     * @return 包含推理过程和最终答案的结果
     */
    public ReasoningResult reasoningChat(String question) {
        try {
            // 构建推理模型的配置选项
            DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                    .model(DeepSeekApi.ChatModel.DEEPSEEK_REASONER.getValue())
                    .temperature(0.1) // 推理模型使用较低温度
                    .build();

            UserMessage userMessage = new UserMessage(question);
            Prompt prompt = new Prompt(List.of(userMessage), options);
            
            ChatResponse response = deepSeekChatClient.prompt(prompt).call().chatResponse();
            
            // 提取推理内容和最终答案
            DeepSeekAssistantMessage assistantMessage = (DeepSeekAssistantMessage) response.getResult().getOutput();
            String reasoningContent = assistantMessage.getReasoningContent();
            String finalAnswer = assistantMessage.getText();
            
            log.info("DeepSeek推理完成 - 问题长度: {}, 推理步骤长度: {}, 答案长度: {}", 
                    question.length(), 
                    reasoningContent != null ? reasoningContent.length() : 0, 
                    finalAnswer != null ? finalAnswer.length() : 0);
            
            return new ReasoningResult(reasoningContent, finalAnswer);
            
        } catch (Exception e) {
            log.error("DeepSeek推理失败", e);
            throw new RuntimeException("推理服务暂时不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 多轮推理对话
     * 正确处理推理内容，避免将reasoning_content传递给下一轮对话
     */
    public String multiRoundReasoning(List<String> conversationHistory) {
        try {
            List<Message> messages = conversationHistory.stream()
                    .map(UserMessage::new)
                    .map(msg -> (Message) msg)
                    .toList();

            DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                    .model(DeepSeekApi.ChatModel.DEEPSEEK_REASONER.getValue())
                    .temperature(0.1)
                    .build();

            Prompt prompt = new Prompt(messages, options);
            ChatResponse response = deepSeekChatClient.prompt(prompt).call().chatResponse();

            DeepSeekAssistantMessage assistantMessage = (DeepSeekAssistantMessage) response.getResult().getOutput();
            
            // 注意：根据官方文档，多轮对话中不应该包含reasoning_content
            // 只返回最终答案用于后续对话
            return Objects.requireNonNull(assistantMessage.getText());
            
        } catch (Exception e) {
            log.error("多轮推理对话失败", e);
            throw new RuntimeException("多轮推理服务暂时不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 检查DeepSeek服务是否可用
     */
    public boolean isAvailable() {
        try {
            ChatResponse response = deepSeekChatClient.prompt("Hello").call().chatResponse();
            return response != null && response.getResult() != null;
        } catch (Exception e) {
            log.warn("DeepSeek服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 推理结果封装类
     */
    public record ReasoningResult(String reasoningProcess, String finalAnswer) {
        public boolean hasReasoning() {
            return reasoningProcess != null && !reasoningProcess.trim().isEmpty();
        }
    }
}
