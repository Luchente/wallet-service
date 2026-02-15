package com.example.walletservice.service;

import com.example.walletservice.domain.OperationType;
import com.example.walletservice.dto.WalletBalanceResponse;
import com.example.walletservice.dto.WalletOperationRequest;
import com.example.walletservice.error.InsufficientFundsException;
import com.example.walletservice.error.WalletNotFoundException;
import com.example.walletservice.persistence.WalletRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository repo;

    public WalletService(WalletRepository repo) {
        this.repo = repo;
    }

    public WalletBalanceResponse operate(WalletOperationRequest req) {
        UUID id = req.walletId();

        BigDecimal amount = req.amount();
        BigDecimal delta = (req.operationType() == OperationType.DEPOSIT) ? amount : amount.negate();

        WalletRepository.ApplyDeltaResult result = repo.applyDelta(id, delta);

        return switch (result.status()) {
            case UPDATED -> new WalletBalanceResponse(id, result.balance());
            case WALLET_NOT_FOUND -> throw new WalletNotFoundException(id);
            case INSUFFICIENT_FUNDS -> throw new InsufficientFundsException(id);
        };
    }

    public WalletBalanceResponse getBalance(UUID walletId) {
        return repo.findBalance(walletId)
                .map(b -> new WalletBalanceResponse(walletId, b))
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }
}