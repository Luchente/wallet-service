package com.example.walletservice;

import com.example.walletservice.domain.OperationType;
import com.example.walletservice.dto.WalletOperationRequest;
import com.example.walletservice.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class WalletConcurrencyTest extends AbstractPostgresTest {

    @Autowired
    private WalletService walletService;

    @Test
    void concurrentDeposits_shouldNotLoseUpdates() throws Exception {
        int threads = 20;
        int perThreadOps = 10; // итого 200 операций
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                await(start);
                for (int i = 0; i < perThreadOps; i++) {
                    walletService.operate(new WalletOperationRequest(
                            WALLET_ID,
                            OperationType.DEPOSIT,
                            BigDecimal.ONE
                    ));
                }
            }));
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }

        pool.shutdown();

        BigDecimal balance = walletService.getBalance(WALLET_ID).balance();
        assertEquals(new BigDecimal("200.00"), balance);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
