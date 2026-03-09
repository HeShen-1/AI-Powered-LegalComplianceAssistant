package com.river.LegalAssistant.config;

import com.river.LegalAssistant.service.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiConfigFallbackTest {

    private final PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
    private final AiConfig aiConfig = new AiConfig(promptTemplateService);

    @Test
    void resolvePreferredChatModelFallsBackToOllamaWhenDeepSeekMissing() {
        ChatModel ollamaChatModel = mock(ChatModel.class);

        ChatModel selected = aiConfig.resolvePreferredChatModel(null, ollamaChatModel, "default");

        assertThat(selected).isSameAs(ollamaChatModel);
    }

    @Test
    void resolvePreferredChatModelUsesDeepSeekWhenAvailable() {
        ChatModel deepSeekChatModel = mock(ChatModel.class);
        ChatModel ollamaChatModel = mock(ChatModel.class);

        ChatModel selected = aiConfig.resolvePreferredChatModel(deepSeekChatModel, ollamaChatModel, "default");

        assertThat(selected).isSameAs(deepSeekChatModel);
    }
}
