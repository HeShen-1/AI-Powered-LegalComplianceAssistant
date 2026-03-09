package com.river.LegalAssistant.service.advanced;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LegalQueryTransformerTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private LegalQueryTransformer transformer;

    @Test
    void transformShouldExpandQueriesWithoutCallingLocalChatModel() {
        List<String> transformedQueries = transformer.transform(
                        Query.from("平台对商品质量争议通常有哪些先行处理义务？"))
                .stream()
                .map(Query::text)
                .toList();

        verifyNoInteractions(chatModel);
        assertThat(transformedQueries)
                .contains("平台对商品质量争议通常有哪些先行处理义务？");
        assertThat(transformedQueries)
                .anySatisfy(query -> assertThat(query)
                        .containsAnyOf("电商", "消费者权益", "商品质量争议", "先行赔付"));
    }
}
