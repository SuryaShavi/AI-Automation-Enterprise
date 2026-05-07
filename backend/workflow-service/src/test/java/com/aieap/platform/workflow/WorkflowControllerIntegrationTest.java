package com.aieap.platform.workflow;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aieap.platform.workflow.kafka.KafkaEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = WorkflowServiceApplication.class,
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
class WorkflowControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowRuntimeService workflowRuntimeService;

    @MockBean
    private KafkaEventPublisher kafkaEventPublisher;

    @Test
    void eventEndpointValidatesRequiredFields() throws Exception {
        mockMvc.perform(post("/workflows/events")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      \"eventType\": \"\"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void eventEndpointReturnsExecutionCount() throws Exception {
        when(workflowRuntimeService.processEvent(eq("task.created"), eq("email-service"), anyMap(), anyString()))
            .thenReturn(1);

        mockMvc.perform(post("/workflows/events")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      \"eventType\": \"task.created\",
                      \"source\": \"email-service\",
                      \"payload\": { \"id\": \"evt-1\" }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.executionsStarted").value(1))
            .andExpect(jsonPath("$.data.eventType").value("task.created"));
    }

    @Test
    void employeeCannotCreateWorkflow() throws Exception {
        mockMvc.perform(post("/workflows")
                .with(jwt()
                    .jwt(jwt -> jwt.claim("userId", "11111111-1111-1111-1111-111111111111"))
                    .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      \"name\": \"Employee workflow\",
                      \"steps\": [\"Read email\", \"Create task\"]
                    }
                    """))
            .andExpect(status().isForbidden());
    }
}
