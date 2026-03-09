package com.river.LegalAssistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekChatEnvironmentPostProcessorTest {

    private final DeepSeekChatEnvironmentPostProcessor postProcessor =
            new DeepSeekChatEnvironmentPostProcessor();

    @Test
    void disablesDeepSeekChatWhenNoApiKeyExists() {
        MockEnvironment environment = new MockEnvironment();

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.deepseek.chat.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("ollama");
    }

    @Test
    void enablesDeepSeekChatWhenApiKeyExists() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DEEPSEEK_API_KEY", "test-key");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.deepseek.chat.enabled")).isEqualTo("true");
        assertThat(environment.containsProperty("spring.ai.model.chat")).isFalse();
    }

    @Test
    void keepsExplicitEnablementSetting() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.deepseek.chat.enabled", "false")
                .withProperty("DEEPSEEK_API_KEY", "test-key");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.deepseek.chat.enabled")).isEqualTo("false");
    }

    @Test
    void keepsExplicitChatModelSelection() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "deepseek");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("deepseek");
        assertThat(environment.getProperty("spring.ai.deepseek.chat.enabled")).isEqualTo("false");
    }
}
