package com.aieap.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aieap.platform.ai.service.PromptTemplateService;
import com.aieap.platform.common.ai.AiProviderProperties;
import com.aieap.platform.common.ai.LlmClient;
import com.aieap.platform.common.ai.TokenEstimator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class AiChatServiceAttachmentResolutionTest {

    private LlmClient llmClient;
    private PromptTemplateService promptTemplateService;
    private AiProviderProperties aiProviderProperties;
    private QdrantVectorStoreClient qdrantVectorStoreClient;
    private TokenEstimator tokenEstimator;
    private JdbcTemplate jdbcTemplate;
    private AiChatService aiChatService;

    @BeforeEach
    void setUp() throws Exception {
        llmClient = Mockito.mock(LlmClient.class);
        promptTemplateService = Mockito.mock(PromptTemplateService.class);
        aiProviderProperties = new AiProviderProperties();
        qdrantVectorStoreClient = Mockito.mock(QdrantVectorStoreClient.class);
        tokenEstimator = new TokenEstimator();
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        aiProviderProperties.setMaxPromptChars(6000);
        aiProviderProperties.setMaxPromptTokens(2000);
        aiProviderProperties.setMaxOutputTokens(300);
        aiProviderProperties.setPlannerMaxOutputTokens(120);
        aiProviderProperties.setMaxContextChars(3000);
        aiProviderProperties.setMaxContextTokens(1200);
        aiProviderProperties.setMaxContextChunks(3);
        aiProviderProperties.setMaxHistoryMessages(6);

        aiChatService = new AiChatService(
            llmClient,
            new ObjectMapper(),
            promptTemplateService,
            aiProviderProperties,
            qdrantVectorStoreClient,
            tokenEstimator
        );

        java.lang.reflect.Field jdbcTemplateField = AiChatService.class.getDeclaredField("jdbcTemplate");
        jdbcTemplateField.setAccessible(true);
        jdbcTemplateField.set(aiChatService, jdbcTemplate);

        when(promptTemplateService.getSystemPrompt(any())).thenReturn("You are an assistant.");
    }

    @Test
    void resolvesAttachmentReferenceToProcessedDocumentIdAndUsesGroundedCitation() {
        String attachmentId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String documentId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

        when(llmClient.isConfigured()).thenReturn(true);
        when(qdrantVectorStoreClient.isAvailable()).thenReturn(false);
        when(llmClient.completion(any(), any(), any())).thenReturn("Grounded answer [policy-0-0]");

        when(jdbcTemplate.query(
            eq("SELECT id::text FROM aieap.documents WHERE id::text = ? OR file_name = ?"),
            any(org.springframework.jdbc.core.RowMapper.class),
            eq(attachmentId),
            eq(attachmentId)
        )).thenReturn(List.of());

        when(jdbcTemplate.query(
            eq("SELECT document_id::text AS document_id FROM aieap.chat_attachments WHERE id::text = ? AND document_id IS NOT NULL"),
            any(org.springframework.jdbc.core.RowMapper.class),
            eq(attachmentId)
        )).thenReturn(List.of(documentId));

        when(jdbcTemplate.query(
            eq(
                "SELECT citation_label, LEFT(content, 700) AS content FROM aieap.document_chunks " +
                "WHERE document_id = ?::uuid " +
                "ORDER BY ts_rank_cd(to_tsvector('english', content), plainto_tsquery('english', ?)) DESC, chunk_index ASC LIMIT ?"
            ),
            any(org.springframework.jdbc.core.RowMapper.class),
            eq(documentId),
            eq("What is the leave policy?"),
            eq(3)
        )).thenAnswer(invocation -> {
            org.springframework.jdbc.core.RowMapper<?> mapper = invocation.getArgument(1);
            java.sql.ResultSet rs = Mockito.mock(java.sql.ResultSet.class);
            when(rs.getString("citation_label")).thenReturn("policy-0-0");
            when(rs.getString("content")).thenReturn("Employees get 12 casual leaves per year.");
            Object mapped = mapper.mapRow(rs, 0);
            return List.of(mapped);
        });

        AiChatService.ChatResult result = aiChatService.answer(
            "document",
            "What is the leave policy?",
            List.of(attachmentId),
            List.of(),
            java.util.UUID.randomUUID()
        );

        assertThat(result.content()).contains("policy-0-0");
        assertThat(result.metadataJson()).contains("\"grounded\":true");
    }
}
