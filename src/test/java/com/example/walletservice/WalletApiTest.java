package com.example.walletservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WalletApiTest extends AbstractPostgresTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deposit_shouldIncreaseBalance() throws Exception {
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"walletId":"00000000-0000-0000-0000-000000000001","operationType":"DEPOSIT","amount":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.walletId").value(WALLET_ID.toString()))
                .andExpect(jsonPath("$.balance").value(1000.0));
    }

    @Test
    void getBalance_shouldReturnBalance() throws Exception {
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"walletId":"00000000-0000-0000-0000-000000000001","operationType":"DEPOSIT","amount":10}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/wallets/{id}", WALLET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(WALLET_ID.toString()))
                .andExpect(jsonPath("$.balance").value(10.0));
    }

    @Test
    void withdraw_whenInsufficientFunds_shouldReturn409() throws Exception {
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"walletId":"00000000-0000-0000-0000-000000000001","operationType":"WITHDRAW","amount":999999}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.path").value("/api/v1/wallet"))
                .andExpect(jsonPath("$.details.walletId").value(WALLET_ID.toString()));
    }

    @Test
    void operation_whenWalletNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"walletId":"00000000-0000-0000-0000-000000000099","operationType":"DEPOSIT","amount":10}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WALLET_NOT_FOUND"))
                .andExpect(jsonPath("$.details.walletId").value("00000000-0000-0000-0000-000000000099"));
    }

    @Test
    void invalidJson_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"walletId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_JSON"))
                .andExpect(jsonPath("$.path").value("/api/v1/wallet"));
    }

    @Test
    void invalidEnumValue_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"walletId":"00000000-0000-0000-0000-000000000001","operationType":"HACK","amount":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.details.field").value("operationType"))
                .andExpect(jsonPath("$.details.allowedValues", hasItems("DEPOSIT", "WITHDRAW")));
    }

    @Test
    void methodNotAllowed_shouldReturn405InOurFormat() throws Exception {
        mockMvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.details.method").value("GET"))
                .andExpect(jsonPath("$.details.supportedMethods", hasItem("POST")));
    }
}