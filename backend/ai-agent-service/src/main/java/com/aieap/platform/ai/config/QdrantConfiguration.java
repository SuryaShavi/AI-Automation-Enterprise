package com.aieap.platform.ai.config;

import com.aieap.platform.ai.QdrantProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QdrantProperties.class)
public class QdrantConfiguration {
}