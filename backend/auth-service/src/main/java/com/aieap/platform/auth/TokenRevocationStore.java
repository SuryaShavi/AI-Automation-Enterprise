package com.aieap.platform.auth;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores revoked JWT token IDs (JTIs) in Redis with an expiry equal to the
 * refresh token TTL.  Falls back to an in-memory set when Redis is unavailable
 * so that local development without Redis still works.
 */
@Component
public class TokenRevocationStore {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationStore.class);
    private static final String KEY_PREFIX = "revoked:jti:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    /** In-memory fallback used when Redis calls throw. */
    private final Set<String> fallback = ConcurrentHashMap.newKeySet();

    public TokenRevocationStore(
        StringRedisTemplate redisTemplate,
        @Value("${security.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofDays(refreshTokenTtlDays);
    }

    /** Mark a JTI as revoked.  Expires automatically after the refresh-token TTL. */
    public void revoke(String jti) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (Exception ex) {
            log.warn("Redis unavailable – storing revoked JTI in memory: {}", ex.getMessage());
            fallback.add(jti);
        }
    }

    /** Returns {@code true} if the JTI has been revoked. */
    public boolean isRevoked(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception ex) {
            log.warn("Redis unavailable – checking in-memory revocation list: {}", ex.getMessage());
            return fallback.contains(jti);
        }
    }
}
