package com.ingesta.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionRequest(
        String transactionId,
        @NotBlank String accountId,
        @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "999999999999.99") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        Instant occurredAt,
        @NotNull @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double latitude,
        @NotNull @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double longitude,
        @NotBlank String merchantId,
        @NotBlank String merchantCategory
) {
}
