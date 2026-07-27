package com.ingesta.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Transaction(
        String transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        Instant ingestedAt,
        Double latitude,
        Double longitude,
        String merchantId,
        String merchantCategory
) {
}
