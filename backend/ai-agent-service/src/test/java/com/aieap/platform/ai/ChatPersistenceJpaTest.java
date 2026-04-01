package com.aieap.platform.ai;

import static org.assertj.core.api.Assertions.assertThatNoException;

import com.aieap.platform.ai.config.JpaConfiguration;
import com.aieap.platform.ai.domain.ChatSession;
import com.aieap.platform.ai.repository.ChatSessionRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai-chat-db;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS aieap",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfiguration.class)
class ChatPersistenceJpaTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS aieap.users (
                id UUID PRIMARY KEY
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS aieap.chat_sessions (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                title VARCHAR(255) NOT NULL,
                summary TEXT,
                context_metadata VARCHAR(4000),
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                updated_at TIMESTAMP WITH TIME ZONE NOT NULL
            )
            """);
    }

    @Test
    void savesChatSessionWithoutAuditingTypeConversionErrors() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO aieap.users (id) VALUES (?)", userId);

        assertThatNoException().isThrownBy(() ->
            chatSessionRepository.saveAndFlush(new ChatSession(userId, "First prompt"))
        );
    }
}
