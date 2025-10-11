package com.river.LegalAssistant;

import com.river.LegalAssistant.config.properties.AppProperties;
import com.river.LegalAssistant.config.properties.AiProperties;
import com.river.LegalAssistant.config.properties.RagProperties;
import com.river.LegalAssistant.config.properties.UploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@SpringBootApplication
@EnableConfigurationProperties({
    AppProperties.class,
    UploadProperties.class,
    RagProperties.class,
    AiProperties.class
})
public class LegalAssistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(LegalAssistantApplication.class, args);
	}

}