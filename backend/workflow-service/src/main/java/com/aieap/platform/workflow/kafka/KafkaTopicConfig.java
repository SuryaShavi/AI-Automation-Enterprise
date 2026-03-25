package com.aieap.platform.workflow.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic workflowStateChangedTopic() {
        return TopicBuilder.name("workflow.state_changed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic workflowStateChangedDlqTopic() {
        return TopicBuilder.name("workflow.state_changed.dlq").partitions(1).replicas(1).build();
    }
}
