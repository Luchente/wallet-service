package com.example.walletservice;

import com.example.walletservice.domain.OperationType;
import com.example.walletservice.dto.WalletBalanceResponse;
import com.example.walletservice.dto.WalletOperationRequest;
import com.example.walletservice.error.ApiErrorResponse;
import com.example.walletservice.persistence.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WalletApiIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wallet")
            .withUsername("wallet")
            .withPassword("wallet");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WalletRepository repo;

    private final UUID walletId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void prepareWallet() {
        jdbc.update("""
                INSERT INTO wallets(id, balance)
                VALUES (?, 0)
                ON CONFLICT (id) DO NOTHING
                """, walletId);

        jdbc.update("UPDATE wallets SET balance = 0 WHERE id = ?", walletId);
    }

    @Test
    void deposit_then_get_balance_ok() {
        var req = new WalletOperationRequest(walletId, OperationType.DEPOSIT, new BigDecimal("1000"));
        ResponseEntity<WalletBalanceResponse> post = rest.postForEntity(url("/api/v1/wallet"), req, WalletBalanceResponse.class);

        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post.getBody()).isNotNull();
        assertThat(post.getBody().walletId()).isEqualTo(walletId);
        assertThat(post.getBody().balance()).isEqualByComparingTo(new BigDecimal("1000.00"));

        ResponseEntity<WalletBalanceResponse> get = rest.getForEntity(url("/api/v1/wallets/" + walletId), WalletBalanceResponse.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get.getBody()).isNotNull();
        assertThat(get.getBody().balance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void withdraw_insufficient_funds_returns_409() {
        var req = new WalletOperationRequest(walletId, OperationType.WITHDRAW, new BigDecimal("10"));
        ResponseEntity<ApiErrorResponse> resp = rest.postForEntity(url("/api/v1/wallet"), req, ApiErrorResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void wallet_not_found_returns_404() {
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000099");
        var req = new WalletOperationRequest(missing, OperationType.DEPOSIT, new BigDecimal("1"));

        ResponseEntity<ApiErrorResponse> resp = rest.postForEntity(url("/api/v1/wallet"), req, ApiErrorResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().errorCode()).isEqualTo("WALLET_NOT_FOUND");
    }

    @Test
    void invalid_json_returns_400() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> bad = new HttpEntity<>("{\"walletId\":", h);

        ResponseEntity<ApiErrorResponse> resp = rest.exchange(url("/api/v1/wallet"), HttpMethod.POST, bad, ApiErrorResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_JSON");
    }

    @Test
    void method_not_allowed_returns_405() {
        ResponseEntity<ApiErrorResponse> resp = rest.getForEntity(url("/api/v1/wallet"), ApiErrorResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().errorCode()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void concurrent_repo_updates_no_lost_updates() throws Exception {
        int threads = 16;
        int perThread = 200;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    repo.applyDelta(walletId, new BigDecimal("1.00"))
                            .orElseThrow(() -> new IllegalStateException("Update failed"));
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        BigDecimal expected = new BigDecimal(threads * perThread).setScale(2);
        BigDecimal actual = repo.findBalance(walletId).orElseThrow();
        assertThat(actual).isEqualByComparingTo(expected);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
