package com.aieap.platform.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:taskdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskDataJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void jpaInfrastructureLoads() {
        assertThat(entityManager).isNotNull();
    }

    @Test
    void transactionalWorkIsRolledBackWhenTestTransactionEnds() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS tx_probe (id INT PRIMARY KEY, label VARCHAR(64))");
        jdbcTemplate.update("INSERT INTO tx_probe (id, label) VALUES (?, ?)", 1, "rollback-check");

        Integer insertedCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tx_probe", Integer.class);
        assertThat(insertedCount).isEqualTo(1);

        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();

        Integer countAfterRollback = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tx_probe", Integer.class);
        assertThat(countAfterRollback).isZero();
    }
}
