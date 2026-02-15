package com.example.walletservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class WalletControllerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("wallet")
                    .withUsername("wallet")
                    .withPassword("wallet");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID walletId;

    @BeforeEach
    void setUp() {
        walletId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        jdbc.update("DELETE FROM wallets");
        jdbc.update("INSERT INTO wallets(id, balance) VALUES (?, 0)", walletId);
    }

    @Test
    void getBalance_ok() throws Exception {
        mvc.perform(get("/api/v1/wallets/{id}", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.balance", closeTo(0.0, 0.0001)));
    }

    @Test
    void getBalance_notFound() throws Exception {
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000999");

        mvc.perform(get("/api/v1/wallets/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/wallets/" + missing))
                .andExpect(jsonPath("$.details.walletId").value(missing.toString()));
    }

    @Test
    void getBalance_invalidUuid_unifiedError() throws Exception {
        mvc.perform(get("/api/v1/wallets/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.details.parameter").value("walletId"))
                .andExpect(jsonPath("$.details.expectedType").value("UUID"));
    }

    @Test
    void deposit_ok() throws Exception {
        String body = """
            {"walletId":"%s","operationType":"DEPOSIT","amount":1000}
            """.formatted(walletId);

        mvc.perform(post("/api/v1/wallet")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.balance", closeTo(1000.0, 0.0001)));
    }

    @Test
    void withdraw_insufficientFunds() throws Exception {
        String body = """
            {"walletId":"%s","operationType":"WITHDRAW","amount":1}
            """.formatted(walletId);

        mvc.perform(post("/api/v1/wallet")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.details.walletId").value(walletId.toString()));
    }

    @Test
    void operation_walletNotFound() throws Exception {
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000999");

        String body = """
            {"walletId":"%s","operationType":"DEPOSIT","amount":10}
            """.formatted(missing);

        mvc.perform(post("/api/v1/wallet")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"))
                .andExpect(jsonPath("$.details.walletId").value(missing.toString()));
    }

    @Test
    void invalidJson() throws Exception {
        mvc.perform(post("/api/v1/wallet")
                        .contentType("application/json")
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_JSON"));
    }

    @Test
    void invalidEnumValue_unifiedError() throws Exception {
        String body = """
            {"walletId":"%s","operationType":"BAD","amount":10}
            """.formatted(walletId);

        mvc.perform(post("/api/v1/wallet")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.details.field").value("operationType"))
                .andExpect(jsonPath("$.details.allowedValues", hasItems("DEPOSIT", "WITHDRAW")));
    }

    @Test
    void validationError_amountTooSmall() throws Exception {
        String body = """
            {"walletId":"%s","operationType":"DEPOSIT","amount":0}
            """.formatted(walletId);

        mvc.perform(post("/api/v1/wallet")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fieldErrors", not(empty())));
    }

    @Test
    void unknownEndpoint_unified404() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
