package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.dto.UnifiedChatRequest;
import com.river.LegalAssistant.dto.UnifiedChatResponse;
import com.river.LegalAssistant.service.AgentService;
import com.river.LegalAssistant.service.AiService;
import com.river.LegalAssistant.service.ChatHistoryService;
import com.river.LegalAssistant.service.ChatMemoryService;
import com.river.LegalAssistant.service.DeepSeekService;
import com.river.LegalAssistant.service.advanced.AdvancedLegalRagService;
import com.river.LegalAssistant.util.JwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedChatControllerTest {

    @Mock
    private AiService aiService;

    @Mock
    private AgentService agentService;

    @Mock
    private DeepSeekService deepSeekService;

    @Mock
    private AdvancedLegalRagService advancedRagService;

    @Mock
    private ChatMemoryService chatMemoryService;

    @Mock
    private ChatHistoryService chatHistoryService;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private UnifiedChatController controller;

    @Test
    void advancedModeShouldFallBackToAvailableAgentWhenDeepSeekIsUnavailable() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setMessage("请分析合同违约责任");
        request.setModelType(UnifiedChatRequest.ModelType.ADVANCED);
        request.setModelName("DEEPSEEK");

        when(deepSeekService.isAvailable()).thenReturn(false);
        when(agentService.consultLegalMatterWithDetails("请分析合同违约责任"))
                .thenReturn(new AgentService.ConsultationResult(
                        "已回退到可用模型并完成分析",
                        "OLLAMA基础服务 (降级)",
                        "qwen2:1.5b",
                        false
                ));

        ResponseEntity<UnifiedChatResponse> entity = controller.chat(request);

        assertThat(entity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getAnswer()).isEqualTo("已回退到可用模型并完成分析");
        assertThat(entity.getBody().getMetadata())
                .containsEntry("fallbackUsed", true)
                .containsEntry("actualModel", "qwen2:1.5b")
                .containsEntry("routeReason", "advanced_direct")
                .containsKey("latencyMs");
    }

    @Test
    void unifiedModeShouldExposeStableMetadataForSimpleQueries() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setMessage("什么是违约责任？");
        request.setModelType(UnifiedChatRequest.ModelType.UNIFIED);
        request.setModelName("OLLAMA");

        when(advancedRagService.advancedLegalChat("什么是违约责任？", "default"))
                .thenReturn(new AdvancedLegalRagService.AdvancedRagResult(
                        "违约责任是合同一方违约后承担的法律责任。",
                        true,
                        2,
                        List.of(new AdvancedLegalRagService.SourceDetail(
                                "违约责任相关法条",
                                "民法典",
                                0.92,
                                "law"
                        )),
                        "default",
                        "SUCCESS",
                        88L
                ));

        ResponseEntity<UnifiedChatResponse> entity = controller.chat(request);

        assertThat(entity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getResponseType()).isEqualTo("unified_to_advanced_rag_simple");
        assertThat(entity.getBody().getMetadata())
                .containsEntry("routeReason", "simple_query")
                .containsEntry("fallbackUsed", false)
                .containsEntry("sourceCount", 2)
                .containsKey("actualModel")
                .containsKey("latencyMs");
    }
}
