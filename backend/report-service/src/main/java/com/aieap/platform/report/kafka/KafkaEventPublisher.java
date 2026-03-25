package com.aieap.platform.report.kafka;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, String key, Object event, String correlationId) {
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
            record.headers().add("X-Correlation-ID", correlationId.getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event to topic={} correlationId={}: {}",
                        topic, correlationId, ex.getMessage());
                } else {
                    log.info("Published event topic={} partition={} offset={} correlationId={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        correlationId);
                }
            });
        } catch (Exception ex) {
            log.error("Error building Kafka message for topic={}: {}", topic, ex.getMessage(), ex);
        }
    }
}
