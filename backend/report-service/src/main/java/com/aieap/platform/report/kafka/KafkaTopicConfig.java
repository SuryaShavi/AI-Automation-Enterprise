package com.aieap.platform.report.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic reportGeneratedTopic() {
        return TopicBuilder.name("report.generated").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic reportGeneratedDlqTopic() {
        return TopicBuilder.name("report.generated.dlq").partitions(1).replicas(1).build();
    }
}
