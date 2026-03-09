package com.river.LegalAssistant.service.advanced;

import com.river.LegalAssistant.service.DeepSeekService;
import com.river.LegalAssistant.service.PromptTemplateService;
import com.river.LegalAssistant.service.TextProcessingService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedLegalRagService {

    @Qualifier("langchain4jChatModel")
    private final ChatModel chatModel;

    private final DeepSeekService deepSeekService;
    private final PromptTemplateService promptTemplateService;
    private final TextProcessingService textProcessingService;
    private final LegalQueryTransformer queryTransformer;
    private final LegalQueryRouter queryRouter;
    private final LegalContentRetriever contentRetriever;
    private final LegalContentAggregator contentAggregator;
    private final LegalContentInjector contentInjector;

    private final Map<String, MessageWindowChatMemory> sessionMemories = new ConcurrentHashMap<>();

    private RetrievalAugmentor retrievalAugmentor;
    private LegalAssistant legalAssistant;
    private boolean initialized = false;
    private String initializationError;

    public interface LegalAssistant {
        Result<String> chatWithSources(String userMessage);
        String chat(String userMessage);
    }

    @PostConstruct
    public void initializeAdvancedRag() {
        log.info("Initializing Advanced Legal RAG service");

        try {
            buildRetrievalAugmentor();
            buildAiService();
            initialized = true;
            initializationError = null;
            log.info("Advanced Legal RAG service initialized");
        } catch (Exception e) {
            initialized = false;
            initializationError = e.getMessage();
            log.error("Failed to initialize Advanced Legal RAG service", e);
        }
    }

    public AdvancedRagResult advancedLegalChat(String question, String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        log.info("Advanced RAG chat request received, sessionId={}", normalizedSessionId);

        if (!initialized) {
            return new AdvancedRagResult(
                    "Advanced RAG service is not initialized: " + initializationError,
                    false,
                    0,
                    Collections.emptyList(),
                    normalizedSessionId,
                    "NOT_INITIALIZED",
                    null,
                    "unknown"
            );
        }

        if (question == null || question.trim().isEmpty()) {
            return new AdvancedRagResult(
                    "Please provide a specific legal question.",
                    false,
                    0,
                    Collections.emptyList(),
                    normalizedSessionId,
                    "EMPTY_QUESTION",
                    null,
                    "unknown"
            );
        }

        try {
            long startTime = System.currentTimeMillis();
            List<Content> retrievedContents = retrieveContents(question, normalizedSessionId);
            List<SourceDetail> sourceDetails = extractSourceDetails(retrievedContents);

            String answer;
            String status = "SUCCESS";
            String generationModel = "deepseek-chat";

            if (deepSeekService.isAvailable()) {
                answer = generateDeepSeekAnswer(question, retrievedContents);
            } else {
                Result<String> fallbackResult = legalAssistant.chatWithSources(question);
                answer = fallbackResult.content();
                if (sourceDetails.isEmpty()) {
                    sourceDetails = extractSourceDetails(fallbackResult.sources());
                }
                status = "DEEPSEEK_UNAVAILABLE_FALLBACK";
                generationModel = "LangChain4j";
            }

            updateSessionMemory(normalizedSessionId, question, answer);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Advanced RAG answer generated, sessionId={}, sourceCount={}, generationModel={}, durationMs={}",
                    normalizedSessionId, sourceDetails.size(), generationModel, duration);

            return new AdvancedRagResult(
                    answer,
                    !sourceDetails.isEmpty(),
                    sourceDetails.size(),
                    sourceDetails,
                    normalizedSessionId,
                    status,
                    duration,
                    generationModel
            );
        } catch (Exception e) {
            log.error("Advanced RAG processing failed", e);
            return new AdvancedRagResult(
                    "Advanced RAG failed to process the question: " + e.getMessage(),
                    false,
                    0,
                    Collections.emptyList(),
                    normalizedSessionId,
                    "PROCESSING_ERROR",
                    null,
                    "unknown"
            );
        }
    }

    public AdvancedRagStatus getStatus() {
        return new AdvancedRagStatus(
                initialized,
                initializationError,
                retrievalAugmentor != null ? "READY" : "NOT_READY",
                legalAssistant != null ? "READY" : "NOT_READY",
                sessionMemories.size(),
                getComponentStatus()
        );
    }

    public void resetSessionMemory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        MessageWindowChatMemory memory = sessionMemories.remove(sessionId);
        if (memory != null) {
            memory.clear();
        }
    }

    public void resetAllSessions() {
        sessionMemories.values().forEach(MessageWindowChatMemory::clear);
        sessionMemories.clear();
    }

    public Map<String, Object> getSessionStatistics() {
        return Map.of(
                "totalSessions", sessionMemories.size(),
                "activeSessions", sessionMemories.entrySet().stream()
                        .filter(entry -> !entry.getValue().messages().isEmpty())
                        .count(),
                "lastUpdate", LocalDateTime.now()
        );
    }

    public void advancedLegalChatStream(String question, String sessionId, SseEmitter emitter, StringBuilder responseBuilder) {
        CompletableFuture.runAsync(() -> {
            String normalizedSessionId = normalizeSessionId(sessionId);
            try {
                AdvancedRagResult result = advancedLegalChat(question, normalizedSessionId);

                emitter.send(SseEmitter.event()
                        .name("start")
                        .data(Map.of("sessionId", result.sessionId())));

                for (String chunk : splitAnswer(result.answer(), 120)) {
                    responseBuilder.append(chunk);
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(Map.of("content", chunk)));
                }

                emitter.send(SseEmitter.event()
                        .name("sources")
                        .data(result.sources().stream().map(SourceDetail::source).distinct().toList()));

                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(Map.of(
                                "status", result.status(),
                                "sourceCount", result.sourceCount(),
                                "modelUsed", result.generationModel(),
                                "duration", result.duration() == null ? 0L : result.duration()
                        )));
                emitter.complete();
            } catch (Exception e) {
                log.error("Advanced RAG stream failed", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("error", e.getMessage())));
                } catch (Exception sendException) {
                    log.warn("Failed to send Advanced RAG stream error", sendException);
                }
                emitter.completeWithError(e);
            }
        });
    }

    private void buildRetrievalAugmentor() {
        this.retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .queryRouter(queryRouter)
                .contentAggregator(contentAggregator)
                .contentInjector(contentInjector)
                .build();
    }

    private void buildAiService() {
        this.legalAssistant = AiServices.builder(LegalAssistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemoryProvider(sessionId ->
                        sessionMemories.computeIfAbsent(String.valueOf(sessionId),
                                ignored -> MessageWindowChatMemory.withMaxMessages(10)))
                .build();
    }

    private List<Content> retrieveContents(String question, String sessionId) {
        UserMessage userMessage = UserMessage.from(question);
        List<ChatMessage> chatMemory = new ArrayList<>(getOrCreateSessionMemory(sessionId).messages());
        AugmentationRequest augmentationRequest = new AugmentationRequest(
                userMessage,
                dev.langchain4j.rag.query.Metadata.from(userMessage, sessionId, chatMemory)
        );
        AugmentationResult augmentationResult = retrievalAugmentor.augment(augmentationRequest);
        if (augmentationResult == null || augmentationResult.contents() == null) {
            return Collections.emptyList();
        }
        return augmentationResult.contents();
    }

    private String generateDeepSeekAnswer(String question, List<Content> contents) {
        String context = buildContext(contents);
        String prompt = promptTemplateService.buildLegalQAPrompt(context, question);
        return deepSeekService.chat(prompt);
    }

    private String buildContext(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "No matching legal knowledge was retrieved from the knowledge base.";
        }

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(contents.size(), 8);
        for (int index = 0; index < limit; index++) {
            Content content = contents.get(index);
            String source = resolveSourceName(extractMetadata(content));
            builder.append("来源: ").append(source).append("\n")
                    .append("内容:\n")
                    .append(truncateText(content.textSegment().text(), 1200))
                    .append("\n\n---\n\n");
        }
        return builder.toString().trim();
    }

    private List<SourceDetail> extractSourceDetails(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyList();
        }

        List<SourceDetail> details = new ArrayList<>();
        for (Content content : contents) {
            SourceDetail detail = convertToSourceDetail(content);
            if (detail != null) {
                details.add(detail);
            }
            if (details.size() >= 10) {
                break;
            }
        }
        return details;
    }

    private SourceDetail convertToSourceDetail(Content content) {
        if (content == null || content.textSegment() == null) {
            return null;
        }

        try {
            Map<String, Object> metadata = extractMetadata(content);
            String text = content.textSegment().text();
            return new SourceDetail(
                    truncateText(text, 200),
                    resolveSourceName(metadata),
                    resolveRelevanceScore(metadata),
                    resolveContentType(metadata, text)
            );
        } catch (Exception e) {
            log.warn("Failed to convert retrieved content into source detail", e);
            return null;
        }
    }

    private Map<String, Object> extractMetadata(Content content) {
        Map<String, Object> metadata = new HashMap<>();
        if (content != null && content.textSegment() != null && content.textSegment().metadata() != null) {
            metadata.putAll(content.textSegment().metadata().toMap());
        }
        return metadata;
    }

    private String resolveSourceName(Map<String, Object> metadata) {
        for (String key : List.of("original_filename", "source", "file_name")) {
            Object value = metadata.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return textProcessingService.cleanFilename(stringValue);
            }
        }
        return "unknown-source";
    }

    private double resolveRelevanceScore(Map<String, Object> metadata) {
        for (String key : List.of("similarity_score", "score", "relevanceScore")) {
            Object value = metadata.get(key);
            if (value instanceof Number numberValue) {
                return numberValue.doubleValue();
            }
            if (value instanceof String stringValue) {
                try {
                    return Double.parseDouble(stringValue);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0.85;
    }

    private String resolveContentType(Map<String, Object> metadata, String text) {
        Object sourceType = metadata.get("source_type");
        if (sourceType instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }

        String normalizedText = text == null ? "" : text.toLowerCase();
        if (normalizedText.contains("case") || normalizedText.contains("案例")) {
            return "case";
        }
        if (normalizedText.contains("article") || normalizedText.contains("法条")) {
            return "law";
        }
        return "knowledge";
    }

    private MessageWindowChatMemory getOrCreateSessionMemory(String sessionId) {
        return sessionMemories.computeIfAbsent(sessionId, ignored -> MessageWindowChatMemory.withMaxMessages(10));
    }

    private void updateSessionMemory(String sessionId, String question, String answer) {
        MessageWindowChatMemory chatMemory = getOrCreateSessionMemory(sessionId);
        chatMemory.add(UserMessage.from(question));
        chatMemory.add(AiMessage.from(answer));
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "default";
        }
        return sessionId;
    }

    private Map<String, String> getComponentStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("queryTransformer", queryTransformer != null ? "READY" : "MISSING");
        status.put("queryRouter", queryRouter != null ? "READY" : "MISSING");
        status.put("contentRetriever", contentRetriever != null ? "READY" : "MISSING");
        status.put("contentAggregator", contentAggregator != null ? "READY" : "MISSING");
        status.put("contentInjector", contentInjector != null ? "READY" : "MISSING");
        status.put("deepSeekService", deepSeekService != null ? "READY" : "MISSING");
        return status;
    }

    private List<String> splitAnswer(String answer, int chunkSize) {
        if (answer == null || answer.isBlank()) {
            return List.of("");
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < answer.length()) {
            int end = Math.min(start + chunkSize, answer.length());
            chunks.add(answer.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    public record AdvancedRagResult(
            String answer,
            boolean hasKnowledgeMatch,
            int sourceCount,
            List<SourceDetail> sources,
            String sessionId,
            String status,
            Long duration,
            String generationModel
    ) {
    }

    public record SourceDetail(
            String content,
            String source,
            double relevanceScore,
            String contentType
    ) {
    }

    public record AdvancedRagStatus(
            boolean initialized,
            String initializationError,
            String retrievalAugmentorStatus,
            String aiServiceStatus,
            int activeSessionCount,
            Map<String, String> componentStatus
    ) {
    }
}
