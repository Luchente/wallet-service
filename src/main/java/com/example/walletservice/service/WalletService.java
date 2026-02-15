package com.example.walletservice.service;

import com.example.walletservice.dto.WalletBalanceResponse;
import com.example.walletservice.dto.WalletOperationRequest;
import com.example.walletservice.domain.OperationType;
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

        BigDecimal delta = (req.operationType() == OperationType.DEPOSIT)
                ? amount
                : amount.negate();

        var updated = repo.applyDelta(id, delta);
        if (updated.isPresent()) {
            return new WalletBalanceResponse(id, updated.get());
        }

        // Неудача: либо кошелька нет, либо денег не хватило.
        if (!repo.exists(id)) {
            throw new WalletNotFoundException(id);
        }
        throw new InsufficientFundsException(id);
    }

    public WalletBalanceResponse getBalance(UUID walletId) {
        return repo.findBalance(walletId)
                .map(b -> new WalletBalanceResponse(walletId, b))
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }
}