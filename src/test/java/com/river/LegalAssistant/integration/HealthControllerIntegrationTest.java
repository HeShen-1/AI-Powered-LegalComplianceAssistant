package com.river.LegalAssistant.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 健康检查控制器集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class HealthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testBasicHealth() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("Legal Assistant"))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    public void testSystemInfo() throws Exception {
        mockMvc.perform(get("/health/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.name").value("Legal Assistant"))
                .andExpect(jsonPath("$.application.version").exists())
                .andExpect(jsonPath("$.java.version").exists())
                .andExpect(jsonPath("$.system.os").exists());
    }

    @Test
    public void testDetailedHealthWithoutAI() throws Exception {
        // 在测试环境中，AI 服务可能不可用，但数据库应该正常
        mockMvc.perform(get("/health/detailed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("Legal Assistant"))
                .andExpect(jsonPath("$.database").exists())
                .andExpect(jsonPath("$.ai").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
