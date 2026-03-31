package com.aieap.platform.notification.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares DLQ topics for all events consumed by the notification-service.
 * The KafkaConsumerConfig error handler routes failed records to <topic>.dlq.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic taskCreatedDlqTopic() {
        return TopicBuilder.name("task.created.dlq").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic taskStatusChangedDlqTopic() {
        return TopicBuilder.name("task.status_changed.dlq").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic newEmailIngestedDlqTopic() {
        return TopicBuilder.name("new.email.ingested.dlq").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic reportGeneratedDlqTopic() {
        return TopicBuilder.name("report.generated.dlq").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic workflowStateChangedDlqTopic() {
        return TopicBuilder.name("workflow.state_changed.dlq").partitions(1).replicas(1).build();
    }
}
