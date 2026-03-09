package com.river.LegalAssistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.LegalAssistant.service.ContractReviewService;
import com.river.LegalAssistant.service.DocumentParserService;
import com.river.LegalAssistant.service.FileStorageService;
import com.river.LegalAssistant.service.ReportGenerationService;
import com.river.LegalAssistant.service.UserService;
import com.river.LegalAssistant.util.JwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractReviewControllerTest {

    @Mock
    private ContractReviewService contractReviewService;

    @Mock
    private UserService userService;

    @Mock
    private DocumentParserService documentParserService;

    @Mock
    private ReportGenerationService reportGenerationService;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ContractReviewController controller;

    @Test
    void analyzeContractAsyncAuthShouldUseAuthorizationHeaderAndStartAnalysis() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer demo-token");

        when(jwtTokenUtil.getUsernameFromToken("demo-token")).thenReturn("demo");
        when(jwtTokenUtil.isTokenExpired("demo-token")).thenReturn(false);
        when(contractReviewService.analyzeContractAsync(eq(42L), any(SseEmitter.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        SseEmitter emitter = controller.analyzeContractAsyncAuth(42L, request, response);

        assertThat(emitter).isNotNull();
        assertThat(response.getContentType()).isEqualTo("text/event-stream;charset=UTF-8");
        verify(contractReviewService).analyzeContractAsync(eq(42L), any(SseEmitter.class));
    }
}
