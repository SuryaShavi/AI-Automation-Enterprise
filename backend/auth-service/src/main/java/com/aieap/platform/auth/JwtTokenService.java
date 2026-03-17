package com.aieap.platform.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final String issuer;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;

    public JwtTokenService(
        JwtEncoder jwtEncoder,
        JwtDecoder jwtDecoder,
        @Value("${security.jwt.issuer}") String issuer,
        @Value("${security.jwt.access-token-ttl-minutes}") long accessTokenMinutes,
        @Value("${security.jwt.refresh-token-ttl-days}") long refreshTokenDays
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.issuer = issuer;
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    public TokenPair createTokenPair(AuthController.ManagedUser user) {
        Instant now = Instant.now();
        Instant accessExpiry = now.plus(accessTokenMinutes, ChronoUnit.MINUTES);
        Instant refreshExpiry = now.plus(refreshTokenDays, ChronoUnit.DAYS);

        String accessToken = issueToken(user, now, accessExpiry, "access");
        String refreshToken = issueToken(user, now, refreshExpiry, "refresh");

        return new TokenPair(accessToken, refreshToken, accessExpiry, refreshExpiry);
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    private String issueToken(AuthController.ManagedUser user, Instant issuedAt, Instant expiresAt, String type) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.email)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claims(existing -> existing.putAll(Map.of(
                "type", type,
                "userId", user.id.toString(),
                "roles", Set.copyOf(user.roles)
            )))
            .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(() -> "HS256").build(), claims)).getTokenValue();
    }

    public record TokenPair(String accessToken, String refreshToken, Instant accessTokenExpiresAt, Instant refreshTokenExpiresAt) {
    }
}