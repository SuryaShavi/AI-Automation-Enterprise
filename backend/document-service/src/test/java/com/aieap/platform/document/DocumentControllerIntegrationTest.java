package com.aieap.platform.document;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aieap.platform.common.ai.LlmClient;
import com.aieap.platform.document.kafka.KafkaEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = DocumentServiceApplication.class,
    properties = {
        "security.jwt.secret=01234567890123456789012345678901",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
@AutoConfigureMockMvc
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentRagService documentRagService;

    @MockBean
    private LlmClient llmClient;

    @MockBean
    private QdrantVectorStoreClient qdrantVectorStoreClient;

    @MockBean
    private DocumentIndexingService documentIndexingService;

    @MockBean
    private KafkaEventPublisher kafkaEventPublisher;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/documents"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void askRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/documents/123e4567-e89b-12d3-a456-426614174000/ask")
                .with(jwt().jwt(jwt -> jwt.claim("userId", "11111111-1111-1111-1111-111111111111")))
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      \"question\": \"\"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
