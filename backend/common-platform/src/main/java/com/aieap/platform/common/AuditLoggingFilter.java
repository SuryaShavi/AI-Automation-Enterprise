package com.aieap.platform.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class AuditLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLoggingFilter.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            String path = request.getRequestURI();
            String traceId = resolveTraceId(request);
            String actorUserId = resolveActorUserId(request);
            String detailsJson = "{\"method\":\"" + escapeJson(request.getMethod())
                + "\",\"status\":" + response.getStatus()
                + ",\"durationMs\":" + durationMs
                + ",\"query\":\"" + escapeJson(request.getQueryString() == null ? "" : request.getQueryString())
                + "\"}";

            if (jdbcTemplate != null) {
                try {
                    jdbcTemplate.update(
                        "INSERT INTO aieap.audit_logs (actor_user_id, service_name, action, resource_type, resource_id, trace_id, details_json) "
                            + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?::jsonb)",
                        actorUserId,
                        serviceName,
                        request.getMethod() + " " + path,
                        "http",
                        path,
                        traceId,
                        detailsJson
                    );
                } catch (Exception ex) {
                    LOGGER.debug("Unable to persist audit record for path {}", path, ex);
                }
            }

            LOGGER.info("AUDIT method={} path={} status={} durationMs={} traceId={} actor={}",
                request.getMethod(), path, response.getStatus(), durationMs, traceId, actorUserId == null ? "anonymous" : actorUserId);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object traceAttribute = request.getAttribute(CorrelationIdFilter.TRACE_ID_ATTRIBUTE);
        if (traceAttribute != null) {
            return traceAttribute.toString();
        }
        String traceHeader = request.getHeader(CorrelationIdFilter.TRACE_ID_HEADER);
        if (traceHeader != null && !traceHeader.isBlank()) {
            return traceHeader;
        }
        return UUID.randomUUID().toString();
    }

    private String resolveActorUserId(HttpServletRequest request) {
        String actorHeader = request.getHeader("X-User-Id");
        return actorHeader == null || actorHeader.isBlank() ? null : actorHeader;
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}