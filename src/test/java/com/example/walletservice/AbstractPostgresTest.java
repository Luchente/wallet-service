package com.example.walletservice;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

@Testcontainers
public abstract class AbstractPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wallet")
            .withUsername("wallet")
            .withPassword("wallet");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Liquibase должен быть включён, чтобы таблица создавалась миграциями
        registry.add("spring.liquibase.enabled", () -> true);
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected static final UUID WALLET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void resetWallet() {
        upsertWallet(WALLET_ID, BigDecimal.ZERO);
    }

    protected void upsertWallet(UUID id, BigDecimal balance) {
        jdbcTemplate.update("""
                INSERT INTO wallets(id, balance)
                VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance
                """, id, balance);
    }
}
