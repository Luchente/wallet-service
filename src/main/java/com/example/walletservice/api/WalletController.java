package com.example.walletservice.api;

import com.example.walletservice.dto.WalletBalanceResponse;
import com.example.walletservice.dto.WalletOperationRequest;
import com.example.walletservice.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WalletController {

    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping("/wallet")
    public WalletBalanceResponse operate(@Valid @RequestBody WalletOperationRequest request) {
        return service.operate(request);
    }

    @GetMapping("/wallets/{walletId}")
    public WalletBalanceResponse getBalance(@PathVariable UUID walletId) {
        return service.getBalance(walletId);
    }
}