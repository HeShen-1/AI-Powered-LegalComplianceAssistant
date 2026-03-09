package com.river.LegalAssistant.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

public class DeepSeekChatEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String ENABLED_PROPERTY_NAME = "spring.ai.deepseek.chat.enabled";
    private static final String MODEL_CHAT_PROPERTY_NAME = "spring.ai.model.chat";
    private static final String PROPERTY_SOURCE_NAME = "deepSeekChatEnablement";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean hasExplicitEnablement = environment.containsProperty(ENABLED_PROPERTY_NAME);
        boolean hasExplicitModelSelection = environment.containsProperty(MODEL_CHAT_PROPERTY_NAME);
        boolean hasApiKey = hasText(environment.getProperty("DEEPSEEK_API_KEY"))
                || hasText(environment.getProperty("spring.ai.deepseek.api-key"))
                || hasText(environment.getProperty("spring.ai.deepseek.chat.api-key"));

        if (hasExplicitEnablement && (hasExplicitModelSelection || hasApiKey)) {
            return;
        }

        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        if (!hasExplicitEnablement) {
            properties.put(ENABLED_PROPERTY_NAME, Boolean.toString(hasApiKey));
        }

        if (!hasExplicitModelSelection && !hasApiKey) {
            properties.put(MODEL_CHAT_PROPERTY_NAME, "ollama");
        }

        if (properties.isEmpty()) {
            return;
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
