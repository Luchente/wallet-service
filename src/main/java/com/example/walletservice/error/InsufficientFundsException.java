package com.example.walletservice.error;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {
    private final UUID walletId;

    public InsufficientFundsException(UUID walletId) {
        super("Insufficient funds for wallet: " + walletId);
        this.walletId = walletId;
    }

    public UUID getWalletId() {
        return walletId;
    }
}