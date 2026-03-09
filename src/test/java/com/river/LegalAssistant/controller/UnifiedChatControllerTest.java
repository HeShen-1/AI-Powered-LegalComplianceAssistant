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
        request.setMessage("Please analyze liability for breach of contract.");
        request.setModelType(UnifiedChatRequest.ModelType.ADVANCED);
        request.setModelName("DEEPSEEK");

        when(deepSeekService.isAvailable()).thenReturn(false);
        when(agentService.consultLegalMatterWithDetails("Please analyze liability for breach of contract."))
                .thenReturn(new AgentService.ConsultationResult(
                        "Fallback answer produced by the available model.",
                        "OLLAMA basic service (fallback)",
                        "qwen2:1.5b",
                        false
                ));

        ResponseEntity<UnifiedChatResponse> entity = controller.chat(request);

        assertThat(entity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getAnswer()).isEqualTo("Fallback answer produced by the available model.");
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
        request.setModelName("DEEPSEEK");

        when(advancedRagService.advancedLegalChat("什么是违约责任？", "default"))
                .thenReturn(new AdvancedLegalRagService.AdvancedRagResult(
                        "Breach liability means the party in breach bears the corresponding legal responsibility.",
                        true,
                        1,
                        List.of(new AdvancedLegalRagService.SourceDetail(
                                "Relevant legal clause for breach liability",
                                "kb-civil-breach.md",
                                0.92,
                                "law"
                        )),
                        "default",
                        "SUCCESS",
                        88L,
                        "deepseek-chat"
                ));

        ResponseEntity<UnifiedChatResponse> entity = controller.chat(request);

        assertThat(entity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getResponseType()).isEqualTo("unified_to_advanced_rag_simple");
        assertThat(entity.getBody().getModelName()).isEqualTo("deepseek-chat");
        assertThat(entity.getBody().getSources())
                .anySatisfy(source -> assertThat(source).contains("kb-civil-breach.md"));
        assertThat(entity.getBody().getMetadata())
                .containsEntry("actualModel", "deepseek-chat")
                .containsEntry("routeReason", "simple_query")
                .containsEntry("fallbackUsed", false)
                .containsEntry("sourceCount", 1)
                .containsKey("latencyMs");
    }

    @Test
    void advancedRagModeShouldExposeStableSourcesAndDeepSeekModel() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setMessage("What are the statutory grounds for terminating a labor contract?");
        request.setModelType(UnifiedChatRequest.ModelType.ADVANCED_RAG);
        request.setModelName("DEEPSEEK");

        when(advancedRagService.advancedLegalChat(
                "What are the statutory grounds for terminating a labor contract?",
                "default"))
                .thenReturn(new AdvancedLegalRagService.AdvancedRagResult(
                        "The statutory grounds include negotiated termination, unilateral termination, and other legally defined cases.",
                        true,
                        1,
                        List.of(new AdvancedLegalRagService.SourceDetail(
                                "Labor contract termination clauses",
                                "kb-labor-termination.md",
                                0.94,
                                "law"
                        )),
                        "default",
                        "SUCCESS",
                        65L,
                        "deepseek-chat"
                ));

        ResponseEntity<UnifiedChatResponse> entity = controller.chat(request);

        assertThat(entity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getModelName()).isEqualTo("deepseek-chat");
        assertThat(entity.getBody().getSources())
                .anySatisfy(source -> assertThat(source).contains("kb-labor-termination.md"));
        assertThat(entity.getBody().getMetadata())
                .containsEntry("actualModel", "deepseek-chat")
                .containsEntry("routeReason", "advanced_rag_direct")
                .containsEntry("sourceCount", 1)
                .containsEntry("fallbackUsed", false);
    }

    @Test
    void unifiedModeShouldRouteKnowledgePreparationQuestionToAdvancedRag() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setMessage("企业向境外提供个人信息前通常要做哪些准备？");
        request.setModelType(UnifiedChatRequest.ModelType.UNIFIED);
        request.setModelName("DEEPSEEK");

        when(advancedRagService.advancedLegalChat("企业向境外提供个人信息前通常要做哪些准备？", "default"))
                .thenReturn(new AdvancedLegalRagService.AdvancedRagResult(
                        "企业通常需要确认合法性基础、开展影响评估，并准备标准合同或认证安排。",
                        true,
                        1,
                        List.of(new AdvancedLegalRagService.SourceDetail(
                                "Cross-border data transfer preparation checklist",
                                "kb-data-cross-border.md",
                                0.91,
                                "law"
                        )),
                        "default",
                        "SUCCESS",
                        72L,
                        "deepseek-chat"
                ));
        ResponseEntity<UnifiedChatResponse> entity = controller.chat(request);

        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getResponseType()).isEqualTo("unified_to_advanced_rag_simple");
        assertThat(entity.getBody().getSources())
                .anySatisfy(source -> assertThat(source).contains("kb-data-cross-border.md"));
        assertThat(entity.getBody().getMetadata())
                .containsEntry("routeReason", "simple_query")
                .containsEntry("actualModel", "deepseek-chat");
    }

    @Test
    void unifiedModeShouldKeepShortRiskQuestionOnKnowledgeBaseRoute() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setMessage("广告文案里使用“最高级”“最佳”等表述会有什么风险？");
        request.setModelType(UnifiedChatRequest.ModelType.UNIFIED);
        request.setModelName("DEEPSEEK");

        when(advancedRagService.advancedLegalChat("广告文案里使用“最高级”“最佳”等表述会有什么风险？", "default"))
                .thenReturn(new AdvancedLegalRagService.AdvancedRagResult(
                        "这类表述容易触发绝对化用语和虚假宣传风险，并可能面临行政处罚。",
                        true,
                        1,
                        List.of(new AdvancedLegalRagService.SourceDetail(
                                "Advertising compliance red lines",
                                "kb-advertising-compliance.md",
                                0.93,
                                "law"
                        )),
                        "default",
                        "SUCCESS",
                        61L,
                        "deepseek-chat"
                ));
        ResponseEntity<UnifiedChatResponse> entity = controller.chat(request);

        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getResponseType()).isEqualTo("unified_to_advanced_rag_simple");
        assertThat(entity.getBody().getSources())
                .anySatisfy(source -> assertThat(source).contains("kb-advertising-compliance.md"));
        assertThat(entity.getBody().getMetadata())
                .containsEntry("routeReason", "simple_query")
                .containsEntry("actualModel", "deepseek-chat");
    }
}
