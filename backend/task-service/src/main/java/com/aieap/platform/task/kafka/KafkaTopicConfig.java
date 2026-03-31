package com.aieap.platform.task.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic taskCreatedTopic() {
        return TopicBuilder.name("task.created").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic taskCreatedDlqTopic() {
        return TopicBuilder.name("task.created.dlq").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic taskStatusChangedTopic() {
        return TopicBuilder.name("task.status_changed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic taskStatusChangedDlqTopic() {
        return TopicBuilder.name("task.status_changed.dlq").partitions(1).replicas(1).build();
    }
}
