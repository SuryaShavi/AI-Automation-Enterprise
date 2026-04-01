package com.aieap.platform.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(basePackages = "com.aieap.platform.ai.repository")
@EnableTransactionManagement
public class JpaConfiguration {
}
