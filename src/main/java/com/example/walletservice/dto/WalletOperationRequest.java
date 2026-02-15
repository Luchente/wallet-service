package com.example.walletservice.dto;

import com.example.walletservice.domain.OperationType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletOperationRequest(
        @NotNull(message = "walletId обязателен")
        @JsonAlias({"valletId", "walletId"})
        UUID walletId,

        @NotNull(message = "operationType обязателен")
        OperationType operationType,

        @NotNull(message = "amount обязателен")
        @DecimalMin(value = "0.01", message = "amount должен быть больше 0")
        @Digits(integer = 19, fraction = 2, message = "amount: максимум 2 знака после запятой")
        BigDecimal amount
) {
}