package com.aieap.platform.common.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiProviderConfiguration {
}
