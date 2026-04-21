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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import java.util.regex.Pattern;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpoint;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.TopicPartitionOffset;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mockito;

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
@Import(TaskControllerIntegrationTest.KafkaTestConfiguration.class)
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

    @TestConfiguration
    static class KafkaTestConfiguration {

        @Bean(name = "kafkaListenerContainerFactory")
        public KafkaListenerContainerFactory<MessageListenerContainer> kafkaListenerContainerFactory() {
            return new KafkaListenerContainerFactory<>() {
                @Override
                public MessageListenerContainer createListenerContainer(KafkaListenerEndpoint endpoint) {
                    return Mockito.mock(MessageListenerContainer.class);
                }

                @Override
                public MessageListenerContainer createContainer(Pattern topicPattern) {
                    return Mockito.mock(MessageListenerContainer.class);
                }

                @Override
                public MessageListenerContainer createContainer(String... topics) {
                    return Mockito.mock(MessageListenerContainer.class);
                }

                @Override
                public MessageListenerContainer createContainer(TopicPartitionOffset... topicPartitions) {
                    return Mockito.mock(MessageListenerContainer.class);
                }
            };
        }
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/tasks"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createTaskRejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/tasks")
                .with(jwt().jwt(jwt -> jwt.claim("userId", "11111111-1111-1111-1111-111111111111")))
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
