package com.aieap.platform.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = "com.aieap.platform")
@EnableKafka
public class AiAgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiAgentServiceApplication.class, args);
    }
}