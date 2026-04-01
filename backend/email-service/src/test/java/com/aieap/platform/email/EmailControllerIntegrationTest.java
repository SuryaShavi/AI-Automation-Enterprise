package com.aieap.platform.email;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aieap.platform.email.kafka.KafkaEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = EmailServiceApplication.class,
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
class EmailControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailAiService emailAiService;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private KafkaEventPublisher kafkaEventPublisher;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/emails"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void statsReturnsAggregatedCounts() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class)))
            .thenReturn(12, 3, 1);

        mockMvc.perform(get("/emails/stats").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.emailsProcessed").value(12))
            .andExpect(jsonPath("$.data.tasksDetected").value(1))
            .andExpect(jsonPath("$.data.pendingReview").value(3));
    }
}
