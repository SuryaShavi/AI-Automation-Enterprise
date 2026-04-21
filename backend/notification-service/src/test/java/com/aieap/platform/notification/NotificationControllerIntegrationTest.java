package com.aieap.platform.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpoint;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.TopicPartitionOffset;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mockito;

@SpringBootTest(
    classes = NotificationServiceApplication.class,
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
@Import(NotificationControllerIntegrationTest.KafkaTestConfiguration.class)
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

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
                public MessageListenerContainer createContainer(String... topics) {
                    return Mockito.mock(MessageListenerContainer.class);
                }

                @Override
                public MessageListenerContainer createContainer(Pattern topicPattern) {
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
        mockMvc.perform(get("/notifications"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void markAllReadReturnsSuccessEnvelope() throws Exception {
        when(jdbcTemplate.update(anyString())).thenReturn(3);

        mockMvc.perform(patch("/notifications/read-all").with(jwt().jwt(jwt -> jwt.claim("userId", "11111111-1111-1111-1111-111111111111"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("all-read"));
    }
}
