package com.aieap.platform.ai;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aieap.platform.ai.config.SecurityConfiguration;
import com.aieap.platform.ai.service.ChatPersistenceService;
import com.aieap.platform.ai.service.PromptTemplateService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AiController.class, PromptTemplateController.class}, properties = {
    "security.jwt.secret=01234567890123456789012345678901"
})
@Import(SecurityConfiguration.class)
@AutoConfigureMockMvc
class AiAgentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatPersistenceService chatPersistenceService;

    @MockBean
    private AiChatService aiChatService;

    @MockBean
    private PromptTemplateService promptTemplateService;

    @Test
    void chatRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/ai/chat")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      \"prompt\": \"Summarize this\",
                      \"mode\": \"general\"
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void promptTemplatesEndpointReturnsArray() throws Exception {
        when(promptTemplateService.getAllActiveTemplates()).thenReturn(List.of());

        mockMvc.perform(get("/ai/templates").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }
}
