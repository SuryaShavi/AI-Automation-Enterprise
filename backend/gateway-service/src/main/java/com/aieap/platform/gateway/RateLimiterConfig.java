package com.aieap.platform.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Configures the Redis-backed rate limiter key resolver for Spring Cloud Gateway.
 * Uses the client IP address as the rate-limit key so each caller gets its
 * own token bucket.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Resolves rate-limit keys by client IP address.
     * Referenced in application.yml as: key-resolver: "#{@ipKeyResolver}"
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
            .map(addr -> addr.getAddress().getHostAddress())
            .defaultIfEmpty("unknown");
    }
}
