package com.aieap.platform.notification.kafka;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /**
     * Retries 3 times with 1-second intervals before routing to the .dlq topic.
     */
    @Bean
    @SuppressWarnings("null")
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> {
                log.error("Routing failed record to DLQ: topic={} partition={} offset={} error={}",
                    record.topic(), record.partition(), record.offset(), ex.getMessage());
                return new TopicPartition(record.topic() + ".dlq", 0);
            }
        );
        // 3 retries, 1 second apart
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
