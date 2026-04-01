package com.aieap.platform.task;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aieap.platform.task.kafka.KafkaEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = TaskServiceApplication.class,
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
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private KafkaEventPublisher kafkaEventPublisher;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/tasks"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createTaskRejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/tasks")
                .with(jwt())
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      \"title\": \"\",
                      \"priority\": \"HIGH\"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
