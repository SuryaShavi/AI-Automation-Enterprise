package com.aieap.platform.report;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aieap.platform.report.kafka.KafkaEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = ReportServiceApplication.class,
    properties = {
        "security.jwt.secret=01234567890123456789012345678901",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
@AutoConfigureMockMvc
class ReportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaEventPublisher kafkaEventPublisher;

    @Test
    void analyticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/reports/analytics"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceHealthForbiddenForEmployee() throws Exception {
        mockMvc.perform(get("/health/services")
                .with(jwt()
                    .jwt(jwt -> jwt.claim("roles", java.util.Set.of("EMPLOYEE")))
                    .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void serviceHealthAllowedForAdmin() throws Exception {
        mockMvc.perform(get("/health/services")
                .with(jwt()
                    .jwt(jwt -> jwt.claim("roles", java.util.Set.of("ADMIN")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].service").value("auth-service"));
    }
}
