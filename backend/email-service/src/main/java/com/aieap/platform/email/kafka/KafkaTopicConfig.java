package com.aieap.platform.email.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic newEmailIngestedTopic() {
        return TopicBuilder.name("new.email.ingested").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic extractedTaskCreatedTopic() {
        return TopicBuilder.name("extracted.task.created").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic newEmailIngestedDlqTopic() {
        return TopicBuilder.name("new.email.ingested.dlq").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic extractedTaskCreatedDlqTopic() {
        return TopicBuilder.name("extracted.task.created.dlq").partitions(1).replicas(1).build();
    }
}
