package com.aieap.platform.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = AuthServiceApplication.class,
    properties = {
        "security.jwt.secret=01234567890123456789012345678901",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
    }
)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    void loginRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      \"email\": \"not-an-email\",
                      \"password\": \"\"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void registerReturnsImmutableBusinessUserCode() throws Exception {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM aieap.users WHERE email = ?"), eq(Integer.class), eq("new.user@example.com")))
            .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("SELECT aieap.next_user_code(?)"), eq(Long.class), eq(false)))
            .thenReturn(2561000L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(post("/auth/register")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      \"email\": \"new.user@example.com\",
                      \"firstName\": \"New\",
                      \"lastName\": \"User\",
                      \"role\": \"USER\",
                      \"password\": \"Password12\"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.user.id").isNotEmpty())
            .andExpect(jsonPath("$.data.user.userCode").value(2561000));
    }

    @Test
    void registerAdminUsesAdminUserCodeSequence() throws Exception {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM aieap.users WHERE email = ?"), eq(Integer.class), eq("admin.user@example.com")))
            .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("SELECT aieap.next_user_code(?)"), eq(Long.class), eq(true)))
            .thenReturn(2560000L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(post("/auth/register")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      \"email\": \"admin.user@example.com\",
                      \"firstName\": \"Admin\",
                      \"lastName\": \"User\",
                      \"role\": \"ADMIN\",
                      \"password\": \"Password12\"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.user.id").isNotEmpty())
            .andExpect(jsonPath("$.data.user.userCode").value(2560000));
    }
}
